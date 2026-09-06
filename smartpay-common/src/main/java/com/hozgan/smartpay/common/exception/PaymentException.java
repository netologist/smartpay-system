package com.hozgan.smartpay.common.exception;

public abstract sealed class PaymentException extends SmartpayDomainException
        permits InsufficientFundsForPaymentException,
                PaymentNotFoundException {

    protected PaymentException(String errorCode, String message) {
        super(errorCode, message);
    }
}
