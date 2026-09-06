package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.enums.IdempotencyStatus;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.payment.entity.IdempotencyRecordId;
import com.hozgan.smartpay.payment.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    @Value("${smartpay.payment.idempotency.ttl-hours:24}")
    private int ttlHours;

    /**
     * Computes a SHA-256 hex digest of the raw request bytes (Tier-1 idempotency check).
     */
    public String computeHash(byte[] requestBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(requestBody);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Tier-2 idempotency: inserts a PROCESSING record or detects an existing one.
     *
     * <ul>
     *   <li>Empty → new request; caller should proceed.</li>
     *   <li>{@link CachedResponse} → COMPLETED hit; caller should return the cached body.</li>
     *   <li>{@link IdempotencyConflictException} → concurrent PROCESSING in-flight.</li>
     *   <li>{@link RequestHashMismatchException} → same key, different payload.</li>
     * </ul>
     *
     * Runs in its own {@code REQUIRES_NEW} transaction so the PROCESSING record commits
     * immediately and is visible to concurrent requests before any gRPC or outbox work begins.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CachedResponse> acquireLock(TenantId tenantId, IdempotencyKey key, String requestHash) {
        IdempotencyRecordId recordId = new IdempotencyRecordId(tenantId.value(), key.value());
        Optional<IdempotencyRecordEntity> existing = repository.findById(recordId);

        if (existing.isPresent()) {
            IdempotencyRecordEntity record = existing.get();
            if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new IdempotencyConflictException(key, IdempotencyStatus.PROCESSING.name());
            }
            if (!record.getRequestHash().equals(requestHash)) {
                throw new RequestHashMismatchException(key);
            }
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                return Optional.of(new CachedResponse(record.getResponseCode(), record.getResponseBody()));
            }
        }

        Instant expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS);
        IdempotencyRecordEntity newRecord = new IdempotencyRecordEntity(
                recordId, requestHash, IdempotencyStatus.PROCESSING, expiresAt);
        try {
            repository.saveAndFlush(newRecord);
        } catch (DataIntegrityViolationException e) {
            // Race condition: a concurrent thread won the insert; treat as in-flight conflict.
            throw new IdempotencyConflictException(key, IdempotencyStatus.PROCESSING.name());
        }
        return Optional.empty();
    }

    /**
     * Marks the idempotency record COMPLETED with the final HTTP response details.
     * Must be called within an active transaction (enforced by {@code MANDATORY} propagation).
     */
    @Transactional(propagation = Propagation.MANDATORY)
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
     * Immutable carrier for a cached idempotency response (HTTP status + serialised JSON body).
     */
    public record CachedResponse(Integer responseCode, String responseBody) {}
}
