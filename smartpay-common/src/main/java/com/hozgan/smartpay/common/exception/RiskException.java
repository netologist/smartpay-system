package com.hozgan.smartpay.common.exception;

public abstract sealed class RiskException extends SmartpayDomainException
        permits RiskEvaluationException, BlacklistedEntityException {

    protected RiskException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected RiskException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
