package com.hozgan.smartpay.notification.model;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;

import java.util.UUID;

public record NotificationDispatchResult(
        UUID notificationId,
        UUID eventId,
        NotificationChannel channel,
        String recipient,
        NotificationStatus status,
        String providerMessageId,
        String errorMessage
) {
    public boolean isSuccessful() {
        return status == NotificationStatus.DISPATCHED;
    }
}
