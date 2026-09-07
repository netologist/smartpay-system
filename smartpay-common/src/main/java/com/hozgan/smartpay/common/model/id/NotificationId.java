package com.hozgan.smartpay.common.model.id;

import com.hozgan.smartpay.common.util.UuidV7;

import java.util.Objects;
import java.util.UUID;

public record NotificationId(UUID value) implements EntityId<UUID> {

    public NotificationId {
        Objects.requireNonNull(value, "NotificationId value cannot be null");
    }

    public static NotificationId generate() {
        return new NotificationId(UuidV7.generate());
    }

    public static NotificationId of(UUID value) {
        return new NotificationId(value);
    }

    public static NotificationId of(String value) {
        Objects.requireNonNull(value, "NotificationId string cannot be null");
        return new NotificationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
