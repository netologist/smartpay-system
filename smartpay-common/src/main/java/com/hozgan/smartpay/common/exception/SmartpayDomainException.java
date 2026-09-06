package com.hozgan.smartpay.common.exception;

import java.time.Instant;
import java.util.Objects;

/**
 * Root sealed domain exception for SmartPay platform.
 * <p>
 * Using sealed hierarchy enables exhaustive pattern matching with Modern Java switch expressions.
 */
public abstract sealed class SmartpayDomainException extends RuntimeException
        permits AccountException,
                LedgerException,
                IdempotencyException,
                LogisticsException,
                PaymentException,
                ReconciliationException,
                EntityNotFoundException {

    private final String errorCode;
    private final Instant timestamp;

    protected SmartpayDomainException(String errorCode, String message) {
        super(Objects.requireNonNull(message, "message cannot be null"));
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode cannot be null");
        this.timestamp = Instant.now();
    }

    protected SmartpayDomainException(String errorCode, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message cannot be null"), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode cannot be null");
        this.timestamp = Instant.now();
    }

    public String errorCode() {
        return errorCode;
    }

    public Instant timestamp() {
        return timestamp;
    }
}
