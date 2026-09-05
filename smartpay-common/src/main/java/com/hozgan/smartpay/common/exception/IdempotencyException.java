package com.hozgan.smartpay.common.exception;

public abstract sealed class IdempotencyException extends SmartpayDomainException
        permits IdempotencyConflictException,
                RequestHashMismatchException,
                DuplicateTransactionException {

    protected IdempotencyException(String errorCode, String message) {
        super(errorCode, message);
    }
}
