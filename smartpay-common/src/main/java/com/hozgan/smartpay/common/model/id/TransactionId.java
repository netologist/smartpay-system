package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) implements EntityId<UUID> {

    public TransactionId {
        Objects.requireNonNull(value, "TransactionId value cannot be null");
    }

    public static TransactionId generate() {
        return new TransactionId(UuidV7.generate());
    }

    public static TransactionId of(UUID value) {
        return new TransactionId(value);
    }

    public static TransactionId of(String value) {
        Objects.requireNonNull(value, "TransactionId string cannot be null");
        return new TransactionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
