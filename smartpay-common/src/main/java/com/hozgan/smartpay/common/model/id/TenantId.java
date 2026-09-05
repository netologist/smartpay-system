package com.hozgan.smartpay.common.model.id;

import java.util.Objects;

public record TenantId(String value) implements EntityId<String> {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TenantId value cannot be blank");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
