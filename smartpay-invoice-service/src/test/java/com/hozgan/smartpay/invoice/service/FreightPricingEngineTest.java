package com.hozgan.smartpay.invoice.service;

import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.invoice.config.FreightPricingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;

@Tag("unit")
class FreightPricingEngineTest {

    private FreightPricingEngine engine;
    private FreightPricingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FreightPricingProperties();
        engine = new FreightPricingEngine(properties);
    }

    @Test
    @DisplayName("AC-2: Freight Invoice Itemized Pricing Calculation for ARTIC 150 miles (£3.50/mile)")
    void ac2_articPricingCalculation() {
        // Given: 150 miles hauled by ARTIC vehicle (£3.50/mile)
        BigDecimal mileage = new BigDecimal("150.00");
        VehicleType vehicle = VehicleType.ARTIC;
        Currency gbp = Currency.getInstance("GBP");

        // When
        InvoicePricing pricing = engine.calculate(vehicle, mileage, gbp);

        // Then
        // Base Amount: 150 * 3.50 = 525.00
        assertThat(pricing.baseAmount().amount()).isEqualByComparingTo("525.00");
        assertThat(pricing.baseAmount().toMinorUnits()).isEqualTo(52500L);

        // Fuel Surcharge (12%): 525.00 * 0.12 = 63.00
        assertThat(pricing.fuelSurcharge().amount()).isEqualByComparingTo("63.00");
        assertThat(pricing.fuelSurcharge().toMinorUnits()).isEqualTo(6300L);

        // VAT (20% on subtotal £588.00): 588.00 * 0.20 = 117.60
        assertThat(pricing.vatAmount().amount()).isEqualByComparingTo("117.60");
        assertThat(pricing.vatAmount().toMinorUnits()).isEqualTo(11760L);

        // Total Gross Amount: 588.00 + 117.60 = 705.60 (70560 pence)
        assertThat(pricing.totalAmount().amount()).isEqualByComparingTo("705.60");
        assertThat(pricing.totalAmount().toMinorUnits()).isEqualTo(70560L);

        // Invariant check: base + fuel + vat == total
        assertThat(pricing.baseAmount().plus(pricing.fuelSurcharge()).plus(pricing.vatAmount()))
                .isEqualTo(pricing.totalAmount());
    }

    @Test
    @DisplayName("AC-3: Multi-Currency Invoice Support (EUR)")
    void ac3_multiCurrencyPricingInEur() {
        // Given: International cross-border load from Dover to Calais invoiced in EUR
        BigDecimal mileage = new BigDecimal("100.00");
        VehicleType vehicle = VehicleType.ARTIC;
        Currency eur = Currency.getInstance("EUR");

        // When
        InvoicePricing pricing = engine.calculate(vehicle, mileage, eur);

        // Then: Currency is preserved across all pricing components
        assertThat(pricing.baseAmount().currency()).isEqualTo(eur);
        assertThat(pricing.fuelSurcharge().currency()).isEqualTo(eur);
        assertThat(pricing.vatAmount().currency()).isEqualTo(eur);
        assertThat(pricing.totalAmount().currency()).isEqualTo(eur);

        // Base: 100 * 3.50 = 350.00 EUR
        assertThat(pricing.baseAmount().amount()).isEqualByComparingTo("350.00");
        // Fuel: 350 * 0.12 = 42.00 EUR
        assertThat(pricing.fuelSurcharge().amount()).isEqualByComparingTo("42.00");
        // VAT: (350 + 42) * 0.20 = 78.40 EUR
        assertThat(pricing.vatAmount().amount()).isEqualByComparingTo("78.40");
        // Total: 350 + 42 + 78.40 = 470.40 EUR
        assertThat(pricing.totalAmount().amount()).isEqualByComparingTo("470.40");
    }

    @ParameterizedTest(name = "Vehicle {0}: rate {1}/mile for 100 miles")
    @CsvSource({
            "VAN, 1.50, 150.00, 18.00, 33.60, 201.60",
            "LUTON, 2.00, 200.00, 24.00, 44.80, 268.80",
            "SEVEN_POINT_FIVE_TONNE, 2.50, 250.00, 30.00, 56.00, 336.00",
            "ARTIC, 3.50, 350.00, 42.00, 78.40, 470.40"
    })
    @DisplayName("All vehicle types pricing calculations with correct rates")
    void allVehicleTypes(VehicleType vehicleType,
                         String expectedRate,
                         String expectedBase,
                         String expectedFuel,
                         String expectedVat,
                         String expectedTotal) {
        BigDecimal mileage = new BigDecimal("100.00");
        Currency gbp = Currency.getInstance("GBP");

        InvoicePricing pricing = engine.calculate(vehicleType, mileage, gbp);

        assertThat(pricing.baseAmount().amount()).isEqualByComparingTo(expectedBase);
        assertThat(pricing.fuelSurcharge().amount()).isEqualByComparingTo(expectedFuel);
        assertThat(pricing.vatAmount().amount()).isEqualByComparingTo(expectedVat);
        assertThat(pricing.totalAmount().amount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Zero or negative mileage is rejected with IllegalArgumentException")
    void negativeOrZeroMileageRejected() {
        Currency gbp = Currency.getInstance("GBP");

        assertThatThrownBy(() -> engine.calculate(VehicleType.VAN, BigDecimal.ZERO, gbp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() -> engine.calculate(VehicleType.VAN, new BigDecimal("-10.00"), gbp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("Null arguments throw NullPointerException")
    void nullArgumentsValidation() {
        Currency gbp = Currency.getInstance("GBP");
        BigDecimal mileage = new BigDecimal("50.00");

        assertThatThrownBy(() -> engine.calculate(null, mileage, gbp))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> engine.calculate(VehicleType.VAN, null, gbp))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> engine.calculate(VehicleType.VAN, mileage, (Currency) null))
                .isInstanceOf(NullPointerException.class);
    }
}
