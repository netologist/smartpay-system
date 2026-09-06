package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) implements EntityId<UUID> {

    public PaymentId {
        Objects.requireNonNull(value, "PaymentId value cannot be null");
    }

    public static PaymentId generate() {
        return new PaymentId(UuidV7.generate());
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }

    public static PaymentId of(String value) {
        Objects.requireNonNull(value, "PaymentId string cannot be null");
        return new PaymentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
