package com.hozgan.smartpay.invoice.service;

import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.invoice.config.FreightPricingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

@Service
public class FreightPricingEngine {

    private final FreightPricingProperties properties;

    public FreightPricingEngine(FreightPricingProperties properties) {
        this.properties = properties;
    }

    public InvoicePricing calculate(VehicleType vehicleType, BigDecimal mileageMiles, Currency currency) {
        Objects.requireNonNull(vehicleType, "vehicleType cannot be null");
        Objects.requireNonNull(mileageMiles, "mileageMiles cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");

        if (mileageMiles.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Mileage must be strictly positive, got: " + mileageMiles);
        }

        BigDecimal ratePerMile = properties.getRateForVehicle(vehicleType);
        BigDecimal baseAmountDecimal = mileageMiles.multiply(ratePerMile);
        Money baseAmount = Money.of(baseAmountDecimal, currency);

        Money fuelSurcharge = baseAmount.times(properties.getFuelSurchargeRate());
        Money subtotal = baseAmount.plus(fuelSurcharge);
        Money vatAmount = subtotal.times(properties.getVatRate());
        Money totalAmount = subtotal.plus(vatAmount);

        return new InvoicePricing(baseAmount, fuelSurcharge, vatAmount, totalAmount);
    }

    public InvoicePricing calculate(VehicleType vehicleType, BigDecimal mileageMiles, String currencyCode) {
        Currency currency = (currencyCode == null || currencyCode.isBlank())
                ? Currency.getInstance(properties.getDefaultCurrency())
                : Currency.getInstance(currencyCode.trim().toUpperCase());
        return calculate(vehicleType, mileageMiles, currency);
    }
}
