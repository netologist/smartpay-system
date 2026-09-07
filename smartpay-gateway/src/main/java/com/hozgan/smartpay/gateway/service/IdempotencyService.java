package com.hozgan.smartpay.gateway.service;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.enums.IdempotencyStatus;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordId;
import com.hozgan.smartpay.gateway.repository.IdempotencyRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Distributed two-tier idempotency state machine over {@code idempotency_records}.
 *
 * <p>Slot states and transitions:
 * <ul>
 *   <li><b>Absent</b> → insert {@code PROCESSING}; caller proxies downstream.</li>
 *   <li><b>{@code PROCESSING}</b> → concurrent in-flight attempt: HTTP 409 conflict.</li>
 *   <li><b>{@code COMPLETED}</b> + identical body hash → replay cached response (HTTP 200/201 replay);
 *       different hash → HTTP 422 tamper rejection.</li>
 *   <li><b>{@code FAILED}</b> → previous attempt did not complete; slot is atomically reclaimed to
 *       {@code PROCESSING} so the client may retry (a failed operation never poisons its key).</li>
 *   <li><b>Expired TTL</b> (any status, including an orphaned {@code PROCESSING} lock) → slot is
 *       atomically reclaimed, restoring the key to a fresh attempt.</li>
 * </ul>
 *
 * <p>{@code acquireLock} runs in a {@code REQUIRES_NEW} transaction so the {@code PROCESSING}
 * marker is committed and visible to concurrent requests before the downstream call begins.
 */
@Service
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final int ttlHours;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            @Value("${smartpay.gateway.idempotency.ttl-hours:24}") int ttlHours) {
        this.repository = repository;
        this.ttlHours = ttlHours;
    }

    /**
     * SHA-256 hex digest of the raw request body bytes (Tier-1 tamper fingerprint).
     */
    public String computeHash(byte[] requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(requestBody));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Tier-2 lock acquisition for a {@code (tenantId, idempotencyKey)} slot.
     *
     * @return empty when this caller owns the {@code PROCESSING} slot and must proceed downstream;
     *         a {@link CachedResponse} when a matching completed attempt can be replayed directly.
     * @throws IdempotencyConflictException a concurrent attempt is in-flight on this key.
     * @throws RequestHashMismatchException the key completed with a different payload body.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedResponse> acquireLock(TenantId tenantId, IdempotencyKey key, String requestHash) {
        IdempotencyRecordId recordId = new IdempotencyRecordId(tenantId.value(), key.value());
        Optional<IdempotencyRecordEntity> existing = repository.findById(recordId);

        if (existing.isPresent()) {
            IdempotencyRecordEntity record = existing.get();
            if (isReclaimable(record)) {
                return reclaimOrEvaluate(recordId, requestHash);
            }
            return evaluateReplayable(record, requestHash);
        }

        Instant expiresAt = expiry();
        IdempotencyRecordEntity newRecord = new IdempotencyRecordEntity(
                recordId, requestHash, IdempotencyStatus.PROCESSING, expiresAt);
        try {
            repository.saveAndFlush(newRecord);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request won the INSERT race for the same key: mirror the in-flight conflict.
            throw new IdempotencyConflictException(key, IdempotencyStatus.PROCESSING.name());
        }
        return Optional.empty();
    }

    /**
     * Marks the slot COMPLETED with the final downstream HTTP result so subsequent identical
     * retries short-circuit from the gateway cache.
     */
    @Transactional
    public void completeIdempotencyRecord(TenantId tenantId, IdempotencyKey key,
                                          int responseCode, String responseBody) {
        IdempotencyRecordId recordId = new IdempotencyRecordId(tenantId.value(), key.value());
        repository.findById(recordId).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseCode(responseCode);
            record.setResponseBody(responseBody);
            repository.save(record);
        });
    }

    /**
     * Releases the slot as FAILED after a downstream error so the client can retry the key.
     * No response is cached for failed attempts.
     */
    @Transactional
    public void failIdempotencyRecord(TenantId tenantId, IdempotencyKey key) {
        IdempotencyRecordId recordId = new IdempotencyRecordId(tenantId.value(), key.value());
        repository.findById(recordId).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setResponseCode(null);
            record.setResponseBody(null);
            repository.save(record);
        });
    }

    private boolean isReclaimable(IdempotencyRecordEntity record) {
        return record.getStatus() == IdempotencyStatus.FAILED || record.getExpiresAt().isBefore(Instant.now());
    }

    /**
     * Atomically reclaims a FAILED/expired slot to PROCESSING. When a concurrent caller wins the
     * reclaim race the fresh row is re-evaluated strictly (no second reclaim).
     */
    private Optional<CachedResponse> reclaimOrEvaluate(IdempotencyRecordId recordId, String requestHash) {
        Instant now = Instant.now();
        int reclaimed = repository.resetForRetry(recordId, requestHash, IdempotencyStatus.PROCESSING,
                IdempotencyStatus.FAILED, now.plus(ttlHours, ChronoUnit.HOURS), now);
        if (reclaimed == 1) {
            return Optional.empty();
        }
        // Lost the race: the winning caller has already re-marked the row (PROCESSING or completed).
        Optional<IdempotencyRecordEntity> refetched = repository.findById(recordId);
        if (refetched.isPresent()) {
            return evaluateReplayable(refetched.get(), requestHash);
        }
        // Row purged between the reclaim attempt and the re-read; treat as a fresh slot.
        try {
            repository.saveAndFlush(new IdempotencyRecordEntity(recordId, requestHash,
                    IdempotencyStatus.PROCESSING, expiry()));
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // A concurrent request re-inserted the purged slot first; it now owns the in-flight lock.
            throw new IdempotencyConflictException(IdempotencyKey.of(recordId.getIdempotencyKey()),
                    IdempotencyStatus.PROCESSING.name());
        }
    }

    private Optional<CachedResponse> evaluateReplayable(IdempotencyRecordEntity record, String requestHash) {
        return switch (record.getStatus()) {
            case PROCESSING -> throw new IdempotencyConflictException(
                    IdempotencyKey.of(record.getId().getIdempotencyKey()), IdempotencyStatus.PROCESSING.name());
            case COMPLETED -> {
                if (!record.getRequestHash().equals(requestHash)) {
                    throw new RequestHashMismatchException(
                            IdempotencyKey.of(record.getId().getIdempotencyKey()));
                }
                if (record.getResponseCode() == null) {
                    // Defensive: a COMPLETED row must always carry a cached status.
                    throw new IdempotencyConflictException(
                            IdempotencyKey.of(record.getId().getIdempotencyKey()), IdempotencyStatus.PROCESSING.name());
                }
                yield Optional.of(new CachedResponse(record.getResponseCode(), record.getResponseBody()));
            }
            case FAILED -> throw new IdempotencyConflictException(
                    IdempotencyKey.of(record.getId().getIdempotencyKey()), IdempotencyStatus.FAILED.name());
        };
    }

    private Instant expiry() {
        return Instant.now().plus(ttlHours, ChronoUnit.HOURS);
    }

    /**
     * Immutable carrier for a cached idempotency response (HTTP status + serialised JSON body).
     */
    public record CachedResponse(Integer responseCode, String responseBody) {
    }
}
