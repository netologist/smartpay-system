package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record LoadId(String value) implements EntityId<String> {

    public LoadId {
        Objects.requireNonNull(value, "LoadId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LoadId value cannot be blank");
        }
    }

    public static LoadId of(String value) {
        return new LoadId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
