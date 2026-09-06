package com.hozgan.smartpay.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentStatusResponse(
        @JsonProperty("paymentId")
        String paymentId,

        @JsonProperty("status")
        String status,

        @JsonProperty("amountInPence")
        long amountInPence,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("endToEndId")
        String endToEndId,

        @JsonProperty("settledAt")
        Instant settledAt,

        @JsonProperty("bankTransactionReference")
        String bankTransactionReference
) {}
