package com.hozgan.smartpay.recon.dto;

/**
 * Summary result returned by the reconciliation engine after processing a statement.
 * Immutable by design; produced once and handed up to the controller layer.
 */
public record ReconciliationSummary(
        int totalLines,
        int matchedLines,
        int discrepancyLines
) {

    public int unmatchedLines() {
        return totalLines - matchedLines - discrepancyLines;
    }
}
