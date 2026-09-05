package com.hozgan.smartpay.common.exception;

public abstract sealed class LedgerException extends SmartpayDomainException
        permits UnbalancedJournalTransactionException,
                CurrencyMismatchException,
                ImmutableLedgerViolationException {

    protected LedgerException(String errorCode, String message) {
        super(errorCode, message);
    }
}
