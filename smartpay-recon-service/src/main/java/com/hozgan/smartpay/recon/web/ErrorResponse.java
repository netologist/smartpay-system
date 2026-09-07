package com.hozgan.smartpay.recon.web;

import java.time.Instant;

/**
 * Structured error payload returned by the recon service on failure.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp
) {
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now());
    }
}
