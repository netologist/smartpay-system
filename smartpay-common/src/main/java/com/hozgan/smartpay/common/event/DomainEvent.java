package com.hozgan.smartpay.common.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String eventType();

    String aggregateId();
}
