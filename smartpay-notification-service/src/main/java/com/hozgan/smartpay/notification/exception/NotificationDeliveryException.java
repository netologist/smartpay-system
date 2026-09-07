package com.hozgan.smartpay.notification.exception;

public class NotificationDeliveryException extends NotificationException {

    private final int statusCode;
    private final boolean retryable;

    public NotificationDeliveryException(String message, int statusCode, boolean retryable) {
        super(message, "ERR_NOTIFICATION_DELIVERY_FAILED");
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public NotificationDeliveryException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, "ERR_NOTIFICATION_DELIVERY_FAILED", cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
