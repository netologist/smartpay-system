package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record ShipperId(UUID value) implements EntityId<UUID> {

    public ShipperId {
        Objects.requireNonNull(value, "ShipperId value cannot be null");
    }

    public static ShipperId generate() {
        return new ShipperId(UuidV7.generate());
    }

    public static ShipperId of(UUID value) {
        return new ShipperId(value);
    }

    public static ShipperId of(String value) {
        Objects.requireNonNull(value, "ShipperId string cannot be null");
        return new ShipperId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
