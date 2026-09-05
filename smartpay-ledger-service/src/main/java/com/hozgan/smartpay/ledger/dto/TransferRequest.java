package com.hozgan.smartpay.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * REST request payload for initiating a double-entry fund transfer.
 * All monetary values are expressed in minor units (pence) to avoid floating-point representation errors.
 */
public record TransferRequest(

        @NotBlank(message = "sourceAccountId is required")
        String sourceAccountId,

        @NotBlank(message = "targetAccountId is required")
        String targetAccountId,

        @NotNull(message = "amountInPence is required")
        @Positive(message = "amountInPence must be positive")
        Long amountInPence,

        @NotBlank(message = "currency is required")
        String currency,

        @NotBlank(message = "referenceType is required")
        String referenceType,

        @NotBlank(message = "referenceId is required")
        String referenceId,

        String description
) {}
