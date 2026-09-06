package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.EndToEndId;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentInitiatedEvent(
        UUID eventId,
        PaymentId paymentId,
        TenantId tenantId,
        AccountId debtorAccountId,
        AccountId creditorAccountId,
        Money amount,
        EndToEndId endToEndId,
        String holdId,
        String paymentMethod,
        String reference,
        Instant occurredAt
) implements DomainEvent {

    public PaymentInitiatedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(debtorAccountId, "debtorAccountId cannot be null");
        Objects.requireNonNull(creditorAccountId, "creditorAccountId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(endToEndId, "endToEndId cannot be null");
        Objects.requireNonNull(holdId, "holdId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static PaymentInitiatedEvent of(
            PaymentId paymentId,
            TenantId tenantId,
            AccountId debtorAccountId,
            AccountId creditorAccountId,
            Money amount,
            EndToEndId endToEndId,
            String holdId,
            String paymentMethod,
            String reference) {
        return new PaymentInitiatedEvent(
                UuidV7.generate(),
                paymentId,
                tenantId,
                debtorAccountId,
                creditorAccountId,
                amount,
                endToEndId,
                holdId,
                paymentMethod,
                reference,
                Instant.now()
        );
    }

    @Override
    public String aggregateId() {
        return paymentId.toString();
    }

    @Override
    public String eventType() {
        return "PAYMENT_INITIATED";
    }
}
