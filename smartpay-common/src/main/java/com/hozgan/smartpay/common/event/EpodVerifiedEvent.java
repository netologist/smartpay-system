package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EpodVerifiedEvent(
        UUID eventId,
        LoadId loadId,
        CarrierId carrierId,
        Instant deliveredAt,
        GeoLocation location,
        Instant occurredAt
) implements DomainEvent {

    public EpodVerifiedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(loadId, "loadId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(deliveredAt, "deliveredAt cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static EpodVerifiedEvent of(LoadId loadId, CarrierId carrierId, Instant deliveredAt, GeoLocation location) {
        return new EpodVerifiedEvent(UuidV7.generate(), loadId, carrierId, deliveredAt, location, Instant.now());
    }

    @Override
    public String eventType() {
        return "EPOD_VERIFIED";
    }

    @Override
    public String aggregateId() {
        return loadId.asString();
    }
}
