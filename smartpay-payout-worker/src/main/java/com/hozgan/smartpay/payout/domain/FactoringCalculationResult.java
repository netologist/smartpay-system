package com.hozgan.smartpay.payout.domain;

import com.hozgan.smartpay.common.model.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object holding the factoring calculation breakdown.
 * Invariant: grossAmount == netPayoutAmount + factoringFee.
 */
public record FactoringCalculationResult(
        Money grossAmount,
        Money factoringFee,
        Money netPayoutAmount,
        BigDecimal feePercentage
) {
    public FactoringCalculationResult {
        Objects.requireNonNull(grossAmount, "grossAmount cannot be null");
        Objects.requireNonNull(factoringFee, "factoringFee cannot be null");
        Objects.requireNonNull(netPayoutAmount, "netPayoutAmount cannot be null");
        Objects.requireNonNull(feePercentage, "feePercentage cannot be null");

        if (!grossAmount.equals(netPayoutAmount.plus(factoringFee))) {
            throw new IllegalArgumentException(String.format(
                    "Invariant violated: Gross amount (%s) must equal net payout (%s) + factoring fee (%s)",
                    grossAmount, netPayoutAmount, factoringFee));
        }
    }
}
