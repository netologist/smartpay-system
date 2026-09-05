package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record EndToEndId(String value) implements EntityId<String> {

    public EndToEndId {
        Objects.requireNonNull(value, "EndToEndId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("EndToEndId value cannot be blank");
        }
    }

    public static EndToEndId of(String value) {
        return new EndToEndId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
