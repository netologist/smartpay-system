package com.hozgan.smartpay.notification.model;

import java.util.Objects;

public record RecipientProfile(
        String name,
        String phone,
        String email,
        String webhookUrl,
        String webhookSecret
) {
    public RecipientProfile {
        Objects.requireNonNull(name, "name cannot be null");
    }

    public static RecipientProfile defaultCarrier(String name) {
        return new RecipientProfile(
                name,
                "+447700900123",
                "carrier-ops@smartpay.io",
                "http://localhost:8089/webhook/carrier",
                "secret-carrier-webhook-key"
        );
    }

    public static RecipientProfile defaultShipper(String name) {
        return new RecipientProfile(
                name,
                "+447700900456",
                "shipper-invoicing@smartpay.io",
                "http://localhost:8089/webhook/shipper",
                "secret-shipper-webhook-key"
        );
    }
}
