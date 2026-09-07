package com.hozgan.smartpay.notification.dto.response;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationLogResponse(
        UUID id,
        UUID eventId,
        String eventType,
        NotificationChannel channel,
        String recipient,
        String templateCode,
        String renderedContent,
        NotificationStatus status,
        String providerMessageId,
        int retryCount,
        String errorMessage,
        Instant createdAt,
        Instant dispatchedAt,
        Instant updatedAt
) {}
