package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record CarrierId(UUID value) implements EntityId<UUID> {

    public CarrierId {
        Objects.requireNonNull(value, "CarrierId value cannot be null");
    }

    public static CarrierId generate() {
        return new CarrierId(UuidV7.generate());
    }

    public static CarrierId of(UUID value) {
        return new CarrierId(value);
    }

    public static CarrierId of(String value) {
        Objects.requireNonNull(value, "CarrierId string cannot be null");
        return new CarrierId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
