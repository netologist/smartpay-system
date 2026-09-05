package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.Money;

public final class UnbalancedJournalTransactionException extends LedgerException {

    private final Money totalDebit;
    private final Money totalCredit;

    public UnbalancedJournalTransactionException(Money totalDebit, Money totalCredit) {
        super("ERR_LEDGER_UNBALANCED",
                String.format("Journal transaction is unbalanced. Total Debit: %s, Total Credit: %s (difference: %s)",
                        totalDebit, totalCredit, totalDebit.minus(totalCredit).abs()));
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
    }

    public Money totalDebit() {
        return totalDebit;
    }

    public Money totalCredit() {
        return totalCredit;
    }
}
