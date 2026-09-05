package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record StatementReference(String value) implements EntityId<String> {

    public StatementReference {
        Objects.requireNonNull(value, "StatementReference value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("StatementReference value cannot be blank");
        }
    }

    public static StatementReference of(String value) {
        return new StatementReference(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
