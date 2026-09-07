package com.hozgan.smartpay.notification.model;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterNotificationPayload(
        UUID eventId,
        String eventType,
        NotificationChannel channel,
        String recipient,
        String renderedContent,
        String errorMessage,
        int retryCount,
        Instant deadLetteredAt
) {
    public static DeadLetterNotificationPayload of(
            UUID eventId,
            String eventType,
            NotificationChannel channel,
            String recipient,
            String renderedContent,
            String errorMessage,
            int retryCount) {
        return new DeadLetterNotificationPayload(
                eventId,
                eventType,
                channel,
                recipient,
                renderedContent,
                errorMessage,
                retryCount,
                Instant.now()
        );
    }
}
