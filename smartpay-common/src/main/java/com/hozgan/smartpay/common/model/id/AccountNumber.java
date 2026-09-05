package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record AccountNumber(String value) implements EntityId<String> {

    public AccountNumber {
        Objects.requireNonNull(value, "AccountNumber value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AccountNumber value cannot be blank");
        }
    }

    public static AccountNumber of(String value) {
        return new AccountNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
