package com.hozgan.smartpay.ledger.dto;

import java.time.Instant;

/**
 * REST response payload for a completed or idempotent-cached fund transfer.
 * All monetary values are expressed in both major (display) and minor (pence) units.
 */
public record TransferResponse(
        String transactionId,
        String status,
        String sourceAccountId,
        String targetAccountId,
        MoneyView amount,
        MoneyView sourceAvailableBalance,
        MoneyView targetAvailableBalance,
        Instant postedAt
) {
    public record MoneyView(String currency, String amount, long amountInPence) {}
}
