package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FactoringPayoutApprovedEvent(
        UUID eventId,
        InvoiceId invoiceId,
        CarrierId carrierId,
        Money grossAmount,
        Money payoutAmount,
        Money factoringFee,
        Instant occurredAt
) implements DomainEvent {

    public FactoringPayoutApprovedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(invoiceId, "invoiceId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(grossAmount, "grossAmount cannot be null");
        Objects.requireNonNull(payoutAmount, "payoutAmount cannot be null");
        Objects.requireNonNull(factoringFee, "factoringFee cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static FactoringPayoutApprovedEvent of(InvoiceId invoiceId, CarrierId carrierId, Money grossAmount, Money payoutAmount, Money factoringFee) {
        return new FactoringPayoutApprovedEvent(UuidV7.generate(), invoiceId, carrierId, grossAmount, payoutAmount, factoringFee, Instant.now());
    }

    public static FactoringPayoutApprovedEvent of(InvoiceId invoiceId, CarrierId carrierId, Money payoutAmount, Money factoringFee) {
        return of(invoiceId, carrierId, payoutAmount.plus(factoringFee), payoutAmount, factoringFee);
    }

    public Money netPayoutAmount() {
        return payoutAmount;
    }
    @Override
    public String eventType() {
        return "FACTORING_PAYOUT_APPROVED";
    }

    @Override
    public String aggregateId() {
        return invoiceId.asString();
    }
}
