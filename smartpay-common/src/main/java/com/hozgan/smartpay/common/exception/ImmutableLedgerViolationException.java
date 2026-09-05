package com.hozgan.smartpay.common.exception;

public final class ImmutableLedgerViolationException extends LedgerException {

    public ImmutableLedgerViolationException(String operation, String entityId) {
        super("ERR_IMMUTABLE_LEDGER_VIOLATION",
                String.format("Financial Audit Violation: Operation '%s' on ledger entity '%s' is strictly prohibited. Ledger is append-only!",
                        operation, entityId));
    }
}
