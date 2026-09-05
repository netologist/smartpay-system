package com.hozgan.smartpay.ledger.dto;

import java.time.Instant;

/**
 * REST response payload for a completed hold reservation.
 */
public record HoldResponse(
        String accountId,
        String holdId,
        long newHoldBalancePence,
        long newAvailableBalancePence,
        Instant createdAt
) {}
