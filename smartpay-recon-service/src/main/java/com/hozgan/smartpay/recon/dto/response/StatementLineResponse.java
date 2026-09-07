package com.hozgan.smartpay.recon.dto.response;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HTTP response body for a single bank statement line
 * returned by GET /api/v1/recon/statements/{id}/lines.
 */
public record StatementLineResponse(
        UUID id,
        UUID statementId,
        String statementReference,
        String endToEndId,
        Money amount,
        EntryType entryType,
        LocalDate bookingDate,
        ReconciliationStatus reconciliationStatus,
        UUID matchedEntryId
) {}
