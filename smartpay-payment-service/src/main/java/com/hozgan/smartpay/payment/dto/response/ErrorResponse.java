package com.hozgan.smartpay.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ErrorResponse(
        @JsonProperty("errorCode")
        String errorCode,

        @JsonProperty("message")
        String message,

        @JsonProperty("timestamp")
        Instant timestamp
) {
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now());
    }
}
