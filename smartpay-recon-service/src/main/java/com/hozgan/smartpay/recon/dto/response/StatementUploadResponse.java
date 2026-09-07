package com.hozgan.smartpay.recon.dto.response;

import com.hozgan.smartpay.common.model.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * HTTP 201 Created response body for POST /api/v1/recon/statements/upload.
 * Summarises the ingested statement and the auto-reconciliation outcome.
 */
public record StatementUploadResponse(
        UUID statementId,
        String statementReference,
        String bankName,
        String accountNumber,
        LocalDate statementDate,
        String currency,
        Money openingBalance,
        Money closingBalance,
        int totalLinesParsed,
        int matchedLines,
        int discrepancyLines,
        Instant uploadedAt
) {}
