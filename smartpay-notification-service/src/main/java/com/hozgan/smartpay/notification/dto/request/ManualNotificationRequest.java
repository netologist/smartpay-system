package com.hozgan.smartpay.notification.dto.request;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record ManualNotificationRequest(
        @NotNull(message = "eventId cannot be null")
        UUID eventId,

        @NotBlank(message = "eventType cannot be blank")
        String eventType,

        @NotNull(message = "channel cannot be null")
        NotificationChannel channel,

        @NotBlank(message = "recipient cannot be blank")
        String recipient,

        @NotBlank(message = "templateCode cannot be blank")
        String templateCode,

        Map<String, String> parameters
) {}
