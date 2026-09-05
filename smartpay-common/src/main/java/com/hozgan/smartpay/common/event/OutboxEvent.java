package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant processedAt
) {

    public OutboxEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(aggregateType, "aggregateType cannot be null");
        Objects.requireNonNull(aggregateId, "aggregateId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public static OutboxEvent from(DomainEvent event, String aggregateType, String payload) {
        return new OutboxEvent(
                UuidV7.generate(),
                aggregateType,
                event.aggregateId(),
                event.eventType(),
                payload,
                Instant.now(),
                null
        );
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    public OutboxEvent markProcessed() {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, createdAt, Instant.now());
    }
}
