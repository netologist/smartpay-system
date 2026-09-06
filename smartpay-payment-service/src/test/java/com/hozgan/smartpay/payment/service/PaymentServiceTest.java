package com.hozgan.smartpay.payment.service;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.dto.request.PaymentRequest;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import com.hozgan.smartpay.payment.entity.TransactionalOutboxEntity;
import com.hozgan.smartpay.payment.repository.TransactionalOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.hozgan.smartpay.payment.mapper.PaymentMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private IdempotencyService idempotencyService;
    @Mock private LedgerGrpcClient ledgerClient;
    @Mock private TransactionalOutboxRepository outboxRepository;
    @Mock private ObjectMapper objectMapper;
    @Spy private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @InjectMocks
    private PaymentService paymentService;

    private static final TenantId TENANT = TenantId.of("TENANT-UK-01");
    private static final IdempotencyKey KEY = IdempotencyKey.of("PAY-KEY-001");
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "self", paymentService);
    }

    @Test
    void initiatePayment_newPayment_holdsFundsAndCommitsOutbox() {
        PaymentRequest req = buildRequest();
        byte[] rawBody = "{}".getBytes();
        String hash = "a".repeat(64);

        when(idempotencyService.computeHash(rawBody)).thenReturn(hash);
        when(idempotencyService.acquireLock(TENANT, KEY, hash)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        com.hozgan.smartpay.common.model.Money holdBalance = com.hozgan.smartpay.common.model.Money.ofGBP("975.00");
        com.hozgan.smartpay.common.model.Money availBalance = com.hozgan.smartpay.common.model.Money.ofGBP("25.00");
        when(ledgerClient.holdFunds(any(), any(), any(), anyString()))
                .thenReturn(new LedgerGrpcClient.HoldResult("HOLD-001", holdBalance, availBalance));

        PaymentResponse result = paymentService.initiatePayment(TENANT, KEY, rawBody, req);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("INITIATED");
        assertThat(result.amountInPence()).isEqualTo(97500L);
        assertThat(result.currency()).isEqualTo("GBP");

        verify(ledgerClient).holdFunds(any(), any(), any(), anyString());
        verify(outboxRepository).save(any());
        verify(idempotencyService).completeIdempotencyRecord(org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.eq(KEY), org.mockito.ArgumentMatchers.eq(201), any());
    }


    @Test
    void initiatePayment_cacheHit_skipLedgerAndOutbox() throws Exception {
        PaymentRequest req = buildRequest();
        byte[] rawBody = "{}".getBytes();
        String cachedJson = "{\"paymentId\":\"cached-id\",\"status\":\"INITIATED\"}";

        when(idempotencyService.computeHash(rawBody)).thenReturn("a".repeat(64));
        when(idempotencyService.acquireLock(TENANT, KEY, "a".repeat(64)))
                .thenReturn(Optional.of(new IdempotencyService.CachedResponse(201, cachedJson)));
        PaymentResponse cachedResponse = new PaymentResponse("cached-id", "INITIATED", 97500, "GBP", "975.00",
                "E2E-TEST", UUID.randomUUID().toString(), UUID.randomUUID().toString(), null);
        when(objectMapper.readValue(cachedJson, PaymentResponse.class)).thenReturn(cachedResponse);

        PaymentResponse result = paymentService.initiatePayment(TENANT, KEY, rawBody, req);

        assertThat(result.paymentId()).isEqualTo("cached-id");
        verify(ledgerClient, never()).holdFunds(any(), any(), any(), anyString());
        verify(outboxRepository, never()).save(any());
    }

    private PaymentRequest buildRequest() {
        return new PaymentRequest(
                "TENANT-UK-01",
                UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"),
                UUID.fromString("0191c7a2-9b24-7f11-9a1c-8e9942a0b124"),
                97500L,
                "GBP",
                "FASTER_PAYMENTS",
                "PAYOUT-INV-0841",
                "20-00-00",
                "12345678"
        );
    }
}
