package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.PaymentId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentSettledEvent(
        UUID eventId,
        PaymentId paymentId,
        CarrierId carrierId,
        ShipperId shipperId,
        Money settledAmount,
        String bankReference,
        String carrierName,
        Instant occurredAt
) implements DomainEvent {

    public PaymentSettledEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(settledAmount, "settledAmount cannot be null");
        Objects.requireNonNull(bankReference, "bankReference cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static PaymentSettledEvent of(
            PaymentId paymentId,
            CarrierId carrierId,
            Money settledAmount,
            String bankReference) {
        return new PaymentSettledEvent(
                UuidV7.generate(),
                paymentId,
                carrierId,
                null,
                settledAmount,
                bankReference,
                null,
                Instant.now()
        );
    }

    public static PaymentSettledEvent of(
            PaymentId paymentId,
            CarrierId carrierId,
            ShipperId shipperId,
            Money settledAmount,
            String bankReference,
            String carrierName) {
        return new PaymentSettledEvent(
                UuidV7.generate(),
                paymentId,
                carrierId,
                shipperId,
                settledAmount,
                bankReference,
                carrierName,
                Instant.now()
        );
    }

    @Override
    public String aggregateId() {
        return paymentId.toString();
    }

    @Override
    public String eventType() {
        return "PAYMENT_SETTLED";
    }
}
