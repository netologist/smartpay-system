package com.hozgan.smartpay.common.event;

import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TransactionId;
import com.hozgan.smartpay.common.util.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionPostedEvent(
        UUID eventId,
        TransactionId transactionId,
        String referenceType,
        String referenceId,
        IdempotencyKey idempotencyKey,
        Instant occurredAt
) implements DomainEvent {

    public LedgerTransactionPostedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(referenceType, "referenceType cannot be null");
        Objects.requireNonNull(referenceId, "referenceId cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static LedgerTransactionPostedEvent of(TransactionId transactionId, String referenceType, String referenceId, IdempotencyKey idempotencyKey) {
        return new LedgerTransactionPostedEvent(UuidV7.generate(), transactionId, referenceType, referenceId, idempotencyKey, Instant.now());
    }

    @Override
    public String eventType() {
        return "LEDGER_TRANSACTION_POSTED";
    }

    @Override
    public String aggregateId() {
        return transactionId.asString();
    }
}
