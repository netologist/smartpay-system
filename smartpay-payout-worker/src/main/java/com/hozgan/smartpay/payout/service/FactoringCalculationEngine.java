package com.hozgan.smartpay.payout.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.payout.domain.FactoringCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain Service for factoring fee calculations and compliance validation.
 * Enforces AC-1: Accurate 2.5% Factoring Fee Calculation using RoundingMode.HALF_UP.
 */
@Service
public class FactoringCalculationEngine {

    private static final Logger log = LoggerFactory.getLogger(FactoringCalculationEngine.class);

    /**
     * Calculates the factoring fee and net disbursement for a given gross invoice amount.
     * Rounding uses RoundingMode.HALF_UP preserving exact currency minor units.
     *
     * @param grossAmount   Total invoice gross amount
     * @param feePercentage Factoring fee rate (e.g. 2.5 for 2.5%)
     * @return FactoringCalculationResult containing gross, fee, net amounts and fee percentage
     */
    public FactoringCalculationResult calculate(Money grossAmount, BigDecimal feePercentage) {
        Objects.requireNonNull(grossAmount, "grossAmount cannot be null");
        Objects.requireNonNull(feePercentage, "feePercentage cannot be null");

        if (!grossAmount.isPositive()) {
            throw new IllegalArgumentException("Gross invoice amount must be strictly positive: " + grossAmount);
        }

        // Calculate fee with RoundingMode.HALF_UP (via Money.percent)
        Money factoringFee = grossAmount.percent(feePercentage);
        Money netPayoutAmount = grossAmount.minus(factoringFee);

        log.debug("Factoring calculated: Gross={}, FeeRate={}% -> Fee={}, NetPayout={}",
                grossAmount, feePercentage, factoringFee, netPayoutAmount);

        return new FactoringCalculationResult(grossAmount, factoringFee, netPayoutAmount, feePercentage);
    }

    /**
     * Anti-Collusion Verification:
     * Ensures the shipper and carrier do not share identical IDs.
     */
    public boolean verifyAntiCollusion(ShipperId shipperId, CarrierId carrierId) {
        if (shipperId == null || carrierId == null) {
            return false;
        }
        return !shipperId.asString().equalsIgnoreCase(carrierId.asString());
    }

    /**
     * Verifies that the net payout does not exceed the carrier's maximum daily advance limit.
     */
    public boolean verifyDailyAdvanceLimit(Money netPayoutAmount, long maxDailyAdvancePence) {
        Objects.requireNonNull(netPayoutAmount, "netPayoutAmount cannot be null");
        return netPayoutAmount.toMinorUnits() <= maxDailyAdvancePence;
    }
}
