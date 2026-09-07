package com.hozgan.smartpay.common.exception;

/**
 * Thrown when the Ledger gRPC service returns an unexpected error during bank reconciliation.
 * Distinct from {@link UnmatchedBankStatementException} which represents a business-level mismatch.
 */
public final class LedgerQueryException extends ReconciliationException {

    public LedgerQueryException(String errorCode, String message) {
        super(errorCode, message);
    }
}
