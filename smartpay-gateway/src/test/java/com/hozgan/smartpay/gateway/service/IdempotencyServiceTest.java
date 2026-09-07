package com.hozgan.smartpay.gateway.service;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.enums.IdempotencyStatus;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordId;
import com.hozgan.smartpay.gateway.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final TenantId TENANT = TenantId.of("TENANT-UK-01");
    private static final IdempotencyKey KEY = IdempotencyKey.of("GW-KEY-001");
    private static final IdempotencyRecordId RECORD_ID = new IdempotencyRecordId(TENANT.value(), KEY.value());
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(idempotencyService, "ttlHours", 24);
    }

    @Test
    void computeHash_producesSha256HexDigest() {
        String hash = idempotencyService.computeHash("{\"amount\":97500}".getBytes());
        assertThat(hash).hasSize(64).matches("[a-f0-9]+");
    }

    @Test
    void acquireLock_newKey_insertsProcessingAndReturnsEmpty() {
        when(repository.findById(RECORD_ID)).thenReturn(Optional.empty());

        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);

        assertThat(result).isEmpty();
        verify(repository).saveAndFlush(any(IdempotencyRecordEntity.class));
    }

    @Test
    void acquireLock_processingStatus_throwsConflict() {
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(
                record(HASH, IdempotencyStatus.PROCESSING, Instant.now().plus(24, ChronoUnit.HOURS))));

        assertThatThrownBy(() -> idempotencyService.acquireLock(TENANT, KEY, HASH))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void acquireLock_completedSameHash_returnsCachedResponse() {
        IdempotencyRecordEntity record = record(HASH, IdempotencyStatus.COMPLETED,
                Instant.now().plus(24, ChronoUnit.HOURS));
        record.setResponseCode(201);
        record.setResponseBody("{\"paymentId\":\"0191c7c4-8891-7000-84a1-00aa4912fa99\"}");
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(record));

        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);

        assertThat(result).isPresent();
        assertThat(result.get().responseCode()).isEqualTo(201);
        assertThat(result.get().responseBody()).contains("0191c7c4-8891-7000-84a1-00aa4912fa99");
    }

    @Test
    void acquireLock_completedDifferentHash_throwsMismatch() {
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(
                record(OTHER_HASH, IdempotencyStatus.COMPLETED, Instant.now().plus(24, ChronoUnit.HOURS))));

        assertThatThrownBy(() -> idempotencyService.acquireLock(TENANT, KEY, HASH))
                .isInstanceOf(RequestHashMismatchException.class);
    }

    @Test
    void acquireLock_failedRecord_isReclaimedForRetry() {
        IdempotencyRecordEntity record = record(OTHER_HASH, IdempotencyStatus.FAILED,
                Instant.now().plus(24, ChronoUnit.HOURS));
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(record));
        when(repository.resetForRetry(eq(RECORD_ID), anyString(), eq(IdempotencyStatus.PROCESSING),
                eq(IdempotencyStatus.FAILED), any(Instant.class), any(Instant.class))).thenReturn(1);

        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);

        assertThat(result).isEmpty();
    }

    @Test
    void acquireLock_expiredCompletedRecord_isReclaimedForRetry() {
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(
                record(HASH, IdempotencyStatus.COMPLETED, Instant.now().minus(1, ChronoUnit.HOURS))));
        when(repository.resetForRetry(eq(RECORD_ID), anyString(), eq(IdempotencyStatus.PROCESSING),
                eq(IdempotencyStatus.FAILED), any(Instant.class), any(Instant.class))).thenReturn(1);

        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);

        assertThat(result).isEmpty();
    }

    @Test
    void acquireLock_lostReclaimRace_onProcessingWinner_throwsConflict() {
        IdempotencyRecordEntity record = record(OTHER_HASH, IdempotencyStatus.FAILED,
                Instant.now().plus(24, ChronoUnit.HOURS));
        when(repository.findById(RECORD_ID))
                .thenReturn(Optional.of(record))
                .thenReturn(Optional.of(record(HASH, IdempotencyStatus.PROCESSING,
                        Instant.now().plus(24, ChronoUnit.HOURS))));
        when(repository.resetForRetry(eq(RECORD_ID), anyString(), eq(IdempotencyStatus.PROCESSING),
                eq(IdempotencyStatus.FAILED), any(Instant.class), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> idempotencyService.acquireLock(TENANT, KEY, HASH))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void completeIdempotencyRecord_persistsResponseForReplay() {
        IdempotencyRecordEntity record = record(HASH, IdempotencyStatus.PROCESSING,
                Instant.now().plus(24, ChronoUnit.HOURS));
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(record));

        idempotencyService.completeIdempotencyRecord(TENANT, KEY, 201, "{\"paymentId\":\"p-1\"}");

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResponseCode()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo("{\"paymentId\":\"p-1\"}");
        verify(repository).save(record);
    }

    @Test
    void failIdempotencyRecord_releasesSlotWithoutResponse() {
        IdempotencyRecordEntity record = record(HASH, IdempotencyStatus.PROCESSING,
                Instant.now().plus(24, ChronoUnit.HOURS));
        record.setResponseCode(201);
        record.setResponseBody("{\"paymentId\":\"p-1\"}");
        when(repository.findById(RECORD_ID)).thenReturn(Optional.of(record));

        idempotencyService.failIdempotencyRecord(TENANT, KEY);

        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(record.getResponseCode()).isNull();
        assertThat(record.getResponseBody()).isNull();
    }

    private IdempotencyRecordEntity record(String hash, IdempotencyStatus status, Instant expiresAt) {
        return new IdempotencyRecordEntity(RECORD_ID, hash, status, expiresAt);
    }
}
