package com.hozgan.smartpay.common.exception;

public abstract sealed class ReconciliationException extends SmartpayDomainException
        permits UnmatchedBankStatementException {

    protected ReconciliationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
