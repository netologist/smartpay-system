package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.IdempotencyKey;

public final class RequestHashMismatchException extends IdempotencyException {

    private final IdempotencyKey key;

    public RequestHashMismatchException(IdempotencyKey key) {
        super("ERR_REQUEST_HASH_MISMATCH",
                String.format("Idempotency key %s was already used with a different request payload", key));
        this.key = key;
    }

    public IdempotencyKey key() {
        return key;
    }
}
