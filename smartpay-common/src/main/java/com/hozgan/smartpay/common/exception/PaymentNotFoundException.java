package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.PaymentId;

public final class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(PaymentId paymentId) {
        super("ERR_PAYMENT_NOT_FOUND", "Payment not found with ID: " + paymentId);
    }
}
