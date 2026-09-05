package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.EndToEndId;
import com.hozgan.smartpay.common.model.id.StatementReference;

public final class UnmatchedBankStatementException extends ReconciliationException {

    private final StatementReference statementReference;
    private final EndToEndId endToEndId;

    public UnmatchedBankStatementException(StatementReference statementReference, EndToEndId endToEndId) {
        super("ERR_UNMATCHED_BANK_STATEMENT",
                String.format("No matching ledger transaction found for statement reference %s and endToEndId %s",
                        statementReference, endToEndId));
        this.statementReference = statementReference;
        this.endToEndId = endToEndId;
    }

    public StatementReference statementReference() {
        return statementReference;
    }

    public EndToEndId endToEndId() {
        return endToEndId;
    }
}
