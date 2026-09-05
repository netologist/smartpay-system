package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.EntityId;

public final class EntityNotFoundException extends SmartpayDomainException {

    private final String entityType;
    private final String entityId;

    public EntityNotFoundException(String entityType, EntityId<?> entityId) {
        super("ERR_ENTITY_NOT_FOUND",
                String.format("%s entity not found with identifier: %s", entityType, entityId.asString()));
        this.entityType = entityType;
        this.entityId = entityId.asString();
    }

    public EntityNotFoundException(String entityType, String entityId) {
        super("ERR_ENTITY_NOT_FOUND",
                String.format("%s entity not found with identifier: %s", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public String entityType() {
        return entityType;
    }

    public String entityId() {
        return entityId;
    }
}
