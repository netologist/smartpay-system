package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record IdempotencyKey(String value) implements EntityId<String> {

    public IdempotencyKey {
        Objects.requireNonNull(value, "IdempotencyKey value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("IdempotencyKey value cannot be blank");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
