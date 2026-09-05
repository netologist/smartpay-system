package com.hozgan.smartpay.common.model;

import java.util.Objects;

public record InvoicePricing(
        Money baseAmount,
        Money fuelSurcharge,
        Money vatAmount,
        Money totalAmount
) {

    public InvoicePricing {
        Objects.requireNonNull(baseAmount, "baseAmount cannot be null");
        Objects.requireNonNull(fuelSurcharge, "fuelSurcharge cannot be null");
        Objects.requireNonNull(vatAmount, "vatAmount cannot be null");
        Objects.requireNonNull(totalAmount, "totalAmount cannot be null");

        Money calculatedTotal = baseAmount.plus(fuelSurcharge).plus(vatAmount);
        if (!calculatedTotal.equals(totalAmount)) {
            throw new IllegalArgumentException(
                    String.format("totalAmount %s does not match sum of base (%s) + fuel (%s) + vat (%s) = %s",
                            totalAmount, baseAmount, fuelSurcharge, vatAmount, calculatedTotal));
        }
    }

    public static InvoicePricing calculate(Money baseAmount, Money fuelSurcharge, Money vatAmount) {
        Money total = baseAmount.plus(fuelSurcharge).plus(vatAmount);
        return new InvoicePricing(baseAmount, fuelSurcharge, vatAmount, total);
    }
}
