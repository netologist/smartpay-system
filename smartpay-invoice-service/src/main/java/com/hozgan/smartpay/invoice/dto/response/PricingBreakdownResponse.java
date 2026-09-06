package com.hozgan.smartpay.invoice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;

/**
 * Itemized freight pricing breakdown DTO utilizing rich {@link Money} value objects
 * with dual representation for machine-friendly minor units.
 */
public record PricingBreakdownResponse(
        Money baseAmount,
        Money fuelSurcharge,
        Money vatAmount,
        Money totalAmount
) {
    public static PricingBreakdownResponse from(InvoicePricing pricing) {
        if (pricing == null) {
            return null;
        }
        return new PricingBreakdownResponse(
                pricing.baseAmount(),
                pricing.fuelSurcharge(),
                pricing.vatAmount(),
                pricing.totalAmount()
        );
    }

    @JsonProperty("baseAmountPence")
    public long getBaseAmountPence() {
        return baseAmount != null ? baseAmount.toMinorUnits() : 0L;
    }

    @JsonProperty("fuelSurchargePence")
    public long getFuelSurchargePence() {
        return fuelSurcharge != null ? fuelSurcharge.toMinorUnits() : 0L;
    }

    @JsonProperty("vatAmountPence")
    public long getVatAmountPence() {
        return vatAmount != null ? vatAmount.toMinorUnits() : 0L;
    }

    @JsonProperty("totalAmountPence")
    public long getTotalAmountPence() {
        return totalAmount != null ? totalAmount.toMinorUnits() : 0L;
    }
}
