package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record InvoiceId(UUID value) implements EntityId<UUID> {

    public InvoiceId {
        Objects.requireNonNull(value, "InvoiceId value cannot be null");
    }

    public static InvoiceId generate() {
        return new InvoiceId(UuidV7.generate());
    }

    public static InvoiceId of(UUID value) {
        return new InvoiceId(value);
    }

    public static InvoiceId of(String value) {
        Objects.requireNonNull(value, "InvoiceId string cannot be null");
        return new InvoiceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
