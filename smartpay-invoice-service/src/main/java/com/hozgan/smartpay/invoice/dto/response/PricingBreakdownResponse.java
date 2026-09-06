package com.hozgan.smartpay.invoice.dto.response;

import com.hozgan.smartpay.common.model.InvoicePricing;

public record PricingBreakdownResponse(
        String baseAmount,
        long baseAmountPence,
        String fuelSurcharge,
        long fuelSurchargePence,
        String vatAmount,
        long vatAmountPence,
        String totalAmount,
        long totalAmountPence
) {
    public static PricingBreakdownResponse from(InvoicePricing pricing) {
        return new PricingBreakdownResponse(
                pricing.baseAmount().amount().toPlainString(),
                pricing.baseAmount().toMinorUnits(),
                pricing.fuelSurcharge().amount().toPlainString(),
                pricing.fuelSurcharge().toMinorUnits(),
                pricing.vatAmount().amount().toPlainString(),
                pricing.vatAmount().toMinorUnits(),
                pricing.totalAmount().amount().toPlainString(),
                pricing.totalAmount().toMinorUnits()
        );
    }
}
