package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.IdempotencyKey;

public final class IdempotencyConflictException extends IdempotencyException {

    private final IdempotencyKey key;

    public IdempotencyConflictException(IdempotencyKey key, String status) {
        super("ERR_IDEMPOTENCY_CONFLICT",
                String.format("Concurrent or conflicting request in progress for idempotency key %s (status: %s)",
                        key, status));
        this.key = key;
    }

    public IdempotencyKey key() {
        return key;
    }
}
