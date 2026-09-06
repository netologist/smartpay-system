package com.hozgan.smartpay.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record PaymentResponse(
        @JsonProperty("paymentId")
        String paymentId,

        @JsonProperty("status")
        String status,

        @JsonProperty("amountInPence")
        long amountInPence,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("endToEndId")
        String endToEndId,

        @JsonProperty("debtorAccountId")
        String debtorAccountId,

        @JsonProperty("creditorAccountId")
        String creditorAccountId,

        @JsonProperty("createdAt")
        Instant createdAt
) {}
