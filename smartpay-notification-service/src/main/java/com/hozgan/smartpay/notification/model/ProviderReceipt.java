package com.hozgan.smartpay.notification.model;

import com.hozgan.smartpay.common.model.enums.NotificationStatus;

import java.util.Objects;

public record ProviderReceipt(
        String providerMessageId,
        NotificationStatus status,
        String details
) {
    public ProviderReceipt {
        Objects.requireNonNull(status, "status cannot be null");
    }

    public static ProviderReceipt success(String providerMessageId) {
        return new ProviderReceipt(providerMessageId, NotificationStatus.DISPATCHED, "Delivered successfully");
    }

    public static ProviderReceipt failed(String details) {
        return new ProviderReceipt(null, NotificationStatus.FAILED, details);
    }
}
