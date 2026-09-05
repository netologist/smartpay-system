package com.hozgan.smartpay.common.exception;

public abstract sealed class LogisticsException extends SmartpayDomainException
        permits InvalidEpodSignatureException,
                DuplicateLoadException,
                InvoiceAlreadySettledException {

    protected LogisticsException(String errorCode, String message) {
        super(errorCode, message);
    }
}
