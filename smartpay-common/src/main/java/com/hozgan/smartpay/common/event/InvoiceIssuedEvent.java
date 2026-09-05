package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvoiceIssuedEvent(
        UUID eventId,
        InvoiceId invoiceId,
        LoadId loadId,
        ShipperId shipperId,
        CarrierId carrierId,
        InvoicePricing pricing,
        Instant occurredAt
) implements DomainEvent {

    public InvoiceIssuedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(invoiceId, "invoiceId cannot be null");
        Objects.requireNonNull(loadId, "loadId cannot be null");
        Objects.requireNonNull(shipperId, "shipperId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(pricing, "pricing cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static InvoiceIssuedEvent of(InvoiceId invoiceId, LoadId loadId, ShipperId shipperId, CarrierId carrierId, InvoicePricing pricing) {
        return new InvoiceIssuedEvent(UuidV7.generate(), invoiceId, loadId, shipperId, carrierId, pricing, Instant.now());
    }

    @Override
    public String eventType() {
        return "INVOICE_ISSUED";
    }

    @Override
    public String aggregateId() {
        return invoiceId.asString();
    }
}
