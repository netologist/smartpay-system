package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) implements EntityId<UUID> {

    public AccountId {
        Objects.requireNonNull(value, "AccountId value cannot be null");
    }

    public static AccountId generate() {
        return new AccountId(UuidV7.generate());
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        Objects.requireNonNull(value, "AccountId string cannot be null");
        return new AccountId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
