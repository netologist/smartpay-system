package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.enums.IdempotencyStatus;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.payment.entity.IdempotencyRecordId;
import com.hozgan.smartpay.payment.repository.IdempotencyRecordRepository;
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
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final TenantId TENANT = TenantId.of("TENANT-UK-01");
    private static final IdempotencyKey KEY = IdempotencyKey.of("PAY-KEY-001");
    private static final String HASH = "a".repeat(64);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(idempotencyService, "ttlHours", 24);
    }

    @Test
    void computeHash_producesSha256HexString() {
        byte[] body = "{\"amount\":97500}".getBytes();
        String hash = idempotencyService.computeHash(body);
        assertThat(hash).hasSize(64).matches("[a-f0-9]+");
    }

    @Test
    void acquireLock_newKey_returnsEmpty() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenReturn(null);
        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);
        assertThat(result).isEmpty();
    }

    @Test
    void acquireLock_processingStatus_throwsConflict() {
        IdempotencyRecordId id = new IdempotencyRecordId(TENANT.value(), KEY.value());
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity(
                id, HASH, IdempotencyStatus.PROCESSING, Instant.now().plus(24, ChronoUnit.HOURS));
        when(repository.findById(any())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> idempotencyService.acquireLock(TENANT, KEY, HASH))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void acquireLock_hashMismatch_throwsMismatch() {
        IdempotencyRecordId id = new IdempotencyRecordId(TENANT.value(), KEY.value());
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity(
                id, "b".repeat(64), IdempotencyStatus.COMPLETED, Instant.now().plus(24, ChronoUnit.HOURS));
        when(repository.findById(any())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> idempotencyService.acquireLock(TENANT, KEY, HASH))
                .isInstanceOf(RequestHashMismatchException.class);
    }

    @Test
    void acquireLock_completedSameHash_returnsCachedResponse() {
        IdempotencyRecordId id = new IdempotencyRecordId(TENANT.value(), KEY.value());
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity(
                id, HASH, IdempotencyStatus.COMPLETED, Instant.now().plus(24, ChronoUnit.HOURS));
        entity.setResponseCode(201);
        entity.setResponseBody("{\"paymentId\":\"test-id\"}");
        when(repository.findById(any())).thenReturn(Optional.of(entity));

        Optional<IdempotencyService.CachedResponse> result = idempotencyService.acquireLock(TENANT, KEY, HASH);

        assertThat(result).isPresent();
        assertThat(result.get().responseCode()).isEqualTo(201);
        assertThat(result.get().responseBody()).contains("test-id");
    }
}
