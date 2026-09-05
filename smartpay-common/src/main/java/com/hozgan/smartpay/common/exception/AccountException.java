package com.hozgan.smartpay.common.exception;

public abstract sealed class AccountException extends SmartpayDomainException
        permits InsufficientFundsException,
                AccountNotFoundException,
                AccountFrozenException {

    protected AccountException(String errorCode, String message) {
        super(errorCode, message);
    }
}
