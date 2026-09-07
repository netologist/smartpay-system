package com.hozgan.smartpay.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartpay.notification")
public record NotificationProperties(
        String consumerGroupId,
        Topics topics,
        Providers providers
) {

    public record Topics(
            String invoiceEvents,
            String paymentEvents,
            String payoutEvents,
            String dlq
    ) {
        public Topics {
            if (invoiceEvents == null) invoiceEvents = "smartpay.events.invoice";
            if (paymentEvents == null) paymentEvents = "smartpay.events.payment";
            if (payoutEvents == null) payoutEvents = "smartpay.events.payout";
            if (dlq == null) dlq = "smartpay.events.notifications.dlq";
        }
    }

    public record Providers(
            TwilioProperties twilio,
            SendGridProperties sendgrid,
            WebhookProperties webhook
    ) {}

    public record TwilioProperties(
            String baseUrl,
            String accountSid,
            String authToken,
            String fromNumber
    ) {
        public TwilioProperties {
            if (baseUrl == null) baseUrl = "http://localhost:8089";
            if (accountSid == null) accountSid = "AC_MOCK_SMARTPAY";
            if (authToken == null) authToken = "mock_auth_token";
            if (fromNumber == null) fromNumber = "+447700900000";
        }
    }

    public record SendGridProperties(
            String baseUrl,
            String apiKey,
            String fromEmail
    ) {
        public SendGridProperties {
            if (baseUrl == null) baseUrl = "http://localhost:8089";
            if (apiKey == null) apiKey = "SG.MOCK_KEY";
            if (fromEmail == null) fromEmail = "notifications@smartpay.io";
        }
    }

    public record WebhookProperties(
            int timeoutSeconds,
            int maxRetries
    ) {
        public WebhookProperties {
            if (timeoutSeconds <= 0) timeoutSeconds = 5;
            if (maxRetries <= 0) maxRetries = 3;
        }
    }
}
