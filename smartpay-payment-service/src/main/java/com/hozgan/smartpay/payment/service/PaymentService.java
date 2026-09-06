package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.common.event.PaymentInitiatedEvent;
import com.hozgan.smartpay.common.exception.EntityNotFoundException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.PaymentStatus;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.EndToEndId;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.dto.request.PaymentRequest;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import com.hozgan.smartpay.payment.dto.response.PaymentStatusResponse;
import com.hozgan.smartpay.payment.entity.TransactionalOutboxEntity;
import com.hozgan.smartpay.payment.mapper.PaymentMapper;
import com.hozgan.smartpay.payment.repository.TransactionalOutboxRepository;
import com.hozgan.smartpay.payment.service.IdempotencyService.CachedResponse;
import com.hozgan.smartpay.payment.service.LedgerGrpcClient.HoldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final IdempotencyService idempotencyService;
    private final LedgerGrpcClient ledgerClient;
    private final TransactionalOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final PaymentMapper paymentMapper;

    /**
     * Self-reference injected lazily to allow calling {@link #commitPaymentAtomically}
     * through the Spring CGLIB proxy, enabling the {@code @Transactional} on that method to work
     * when invoked from within the same bean.
     */
    @Autowired
    @Lazy
    private PaymentService self;

    /**
     * Initiates a payment:
     * <ol>
     *   <li>Hash the raw body for Tier-1 idempotency comparison.</li>
     *   <li>Acquire a DB-backed idempotency lock (REQUIRES_NEW TX, commits immediately).</li>
     *   <li>Short-circuit on a COMPLETED cache hit.</li>
     *   <li>Reserve funds on the ledger via gRPC (no DB TX held open).</li>
     *   <li>Atomically persist the outbox event and mark idempotency COMPLETED in one TX.</li>
     * </ol>
     */
    public PaymentResponse initiatePayment(TenantId tenantId, IdempotencyKey idempotencyKey,
                                           byte[] rawRequestBody, PaymentRequest request) {
        String requestHash = idempotencyService.computeHash(rawRequestBody);

        // Tier-2: DB lock in its own REQUIRES_NEW transaction
        Optional<CachedResponse> cached = idempotencyService.acquireLock(tenantId, idempotencyKey, requestHash);
        if (cached.isPresent()) {
            log.debug("Idempotency cache hit for key={}", idempotencyKey);
            return deserializeResponse(cached.get().responseBody());
        }

        PaymentId paymentId = PaymentId.generate();
        EndToEndId endToEndId = EndToEndId.of(buildEndToEndId(paymentId));
        AccountId debtorAccountId = AccountId.of(request.debtorAccountId());
        AccountId creditorAccountId = AccountId.of(request.creditorAccountId());
        Money amount = Money.ofMinor(request.amountInPence(), Currency.getInstance(request.currencyCode()));

        // gRPC call — no transaction open, avoids holding a DB connection during network I/O
        HoldResult holdResult = ledgerClient.holdFunds(debtorAccountId, amount, endToEndId, idempotencyKey.value());

        // Atomic commit: outbox insert + idempotency COMPLETED in the same local transaction
        return self.commitPaymentAtomically(
                tenantId, idempotencyKey, paymentId,
                debtorAccountId, creditorAccountId, amount,
                endToEndId, holdResult.holdId(), request);
    }

    /**
     * Commits the payment outbox entry and idempotency completion record atomically.
     * Must be called via the Spring proxy (use {@link #self}) so the {@code @Transactional}
     * boundary is honoured.
     */
    @Transactional
    public PaymentResponse commitPaymentAtomically(
            TenantId tenantId, IdempotencyKey idempotencyKey, PaymentId paymentId,
            AccountId debtorAccountId, AccountId creditorAccountId, Money amount,
            EndToEndId endToEndId, String holdId, PaymentRequest request) {

        PaymentInitiatedEvent event = PaymentInitiatedEvent.of(
                paymentId, tenantId, debtorAccountId, creditorAccountId, amount,
                endToEndId, holdId, request.paymentMethod(), request.reference());

        String eventPayload = serializeEvent(event);
        TransactionalOutboxEntity outboxEntry = new TransactionalOutboxEntity(
                "PAYMENT", paymentId.toString(), "PAYMENT_INITIATED", eventPayload);
        outboxRepository.save(outboxEntry);

        PaymentResponse response = paymentMapper.toResponse(event);

        String responseJson = serializeResponse(response);
        idempotencyService.completeIdempotencyRecord(tenantId, idempotencyKey, 201, responseJson);

        log.info("Payment initiated: paymentId={} endToEndId={} amount={}", paymentId, endToEndId, amount);
        return response;
    }

    /**
     * Returns the current status of a payment by reading its outbox entry.
     * <p>
     * In STORY-003 scope, only the {@code PAYMENT_INITIATED} event exists, so the status
     * returned is always {@code INITIATED}. Settlement and failure transitions arrive in later stories.
     *
     * @throws EntityNotFoundException if no outbox record exists for the given paymentId
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(PaymentId paymentId) {
        List<TransactionalOutboxEntity> entries =
                outboxRepository.findByAggregateTypeAndAggregateId("PAYMENT", paymentId.toString());

        if (entries.isEmpty()) {
            throw new EntityNotFoundException("Payment", paymentId.toString());
        }

        // Use the first (initiating) event to populate the response
        TransactionalOutboxEntity initiatingEvent = entries.getFirst();

        try {
            JsonNode root = objectMapper.readTree(initiatingEvent.getPayload());
            long minorUnits = root.path("amount").path("minorUnits").asLong(0);
            String currency = root.path("amount").path("currency").asText("GBP");
            // endToEndId is serialised as a scalar string by EntityIdSerializer
            String endToEndId = root.path("endToEndId").asText("");

            return new PaymentStatusResponse(
                    paymentId.toString(),
                    PaymentStatus.INITIATED.name(),
                    minorUnits,
                    currency,
                    endToEndId,
                    null,  // settledAt — not yet available in STORY-003
                    null   // bankTransactionReference — not yet available in STORY-003
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse outbox payload for paymentId=" + paymentId, e);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private String buildEndToEndId(PaymentId paymentId) {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now());
        String shortId = paymentId.toString().substring(paymentId.toString().length() - 4);
        return "E2E-SMARTPAY-" + date + "-" + shortId;
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached payment response", e);
        }
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payment event", e);
        }
    }

    private String serializeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize payment response", e);
        }
    }
}
