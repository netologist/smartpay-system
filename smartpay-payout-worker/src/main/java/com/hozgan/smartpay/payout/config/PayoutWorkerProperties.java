package com.hozgan.smartpay.payout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "smartpay.payout")
public record PayoutWorkerProperties(
        String escrowAccountId,
        String tenantId,
        BigDecimal factoringFeePercentage,
        int riskScoreThreshold,
        long maxDailyAdvancePence,
        String invoiceServiceUrl,
        String riskGrpcHost,
        int riskGrpcPort,
        String paymentGrpcHost,
        int paymentGrpcPort,
        Topics topics,
        String consumerGroupId
) {
    public PayoutWorkerProperties {
        if (escrowAccountId == null || escrowAccountId.isBlank()) {
            escrowAccountId = "0191c7a2-9b24-7f11-9a1c-3d842b10a512";
        }
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "TENANT-UK-01";
        }
        if (factoringFeePercentage == null) {
            factoringFeePercentage = new BigDecimal("2.5");
        }
        if (riskScoreThreshold <= 0) {
            riskScoreThreshold = 40;
        }
        if (maxDailyAdvancePence <= 0) {
            maxDailyAdvancePence = 5000000L;
        }
        if (invoiceServiceUrl == null || invoiceServiceUrl.isBlank()) {
            invoiceServiceUrl = "http://localhost:8081";
        }
        if (riskGrpcHost == null || riskGrpcHost.isBlank()) {
            riskGrpcHost = "localhost";
        }
        if (riskGrpcPort <= 0) {
            riskGrpcPort = 9094;
        }
        if (paymentGrpcHost == null || paymentGrpcHost.isBlank()) {
            paymentGrpcHost = "localhost";
        }
        if (paymentGrpcPort <= 0) {
            paymentGrpcPort = 9092;
        }
        if (topics == null) {
            topics = new Topics("smartpay.events.invoice", "smartpay.events.factoring");
        }
        if (consumerGroupId == null || consumerGroupId.isBlank()) {
            consumerGroupId = "smartpay-factoring-workers";
        }
    }

    public record Topics(
            String invoiceEvents,
            String factoringEvents
    ) {
        public Topics {
            if (invoiceEvents == null || invoiceEvents.isBlank()) {
                invoiceEvents = "smartpay.events.invoice";
            }
            if (factoringEvents == null || factoringEvents.isBlank()) {
                factoringEvents = "smartpay.events.factoring";
            }
        }
    }
}
