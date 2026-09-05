package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.IdempotencyKey;

public final class DuplicateTransactionException extends IdempotencyException {

    private final IdempotencyKey key;

    public DuplicateTransactionException(IdempotencyKey key) {
        super("ERR_DUPLICATE_TRANSACTION",
                String.format("Duplicate transaction detected for idempotency key: %s", key));
        this.key = key;
    }

    public IdempotencyKey key() {
        return key;
    }
}
