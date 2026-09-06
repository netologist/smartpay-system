package com.hozgan.smartpay.payout.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.payout.domain.FactoringCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("FactoringCalculationEngine — Unit Tests")
class FactoringCalculationEngineTest {

    private FactoringCalculationEngine engine;
    private static final BigDecimal DEFAULT_FEE_PERCENT = new BigDecimal("2.5");

    @BeforeEach
    void setUp() {
        engine = new FactoringCalculationEngine();
    }

    @Test
    @DisplayName("AC-1: Accurate 2.5% Factoring Fee Calculation on £1,000.00 GBP invoice")
    void shouldCalculateAccurateFactoringFeeAndNetDisbursementForStandardInvoice() {
        // Given
        Money gross = Money.of("1000.00", Money.GBP);

        // When
        FactoringCalculationResult result = engine.calculate(gross, DEFAULT_FEE_PERCENT);

        // Then
        assertThat(result.grossAmount()).isEqualTo(Money.of("1000.00", Money.GBP));
        assertThat(result.factoringFee()).isEqualTo(Money.of("25.00", Money.GBP));
        assertThat(result.netPayoutAmount()).isEqualTo(Money.of("975.00", Money.GBP));
        assertThat(result.factoringFee().toMinorUnits()).isEqualTo(2500L);
        assertThat(result.netPayoutAmount().toMinorUnits()).isEqualTo(97500L);

        // Invariant: gross == net + fee
        assertThat(result.grossAmount()).isEqualTo(result.netPayoutAmount().plus(result.factoringFee()));
    }

    @Test
    @DisplayName("AC-1: Rounding uses RoundingMode.HALF_UP preserving exact currency minor units")
    void shouldApplyHalfUpRoundingPreservingMinorUnits() {
        // Given: £1,000.55 * 2.5% = 25.01375 -> rounded HALF_UP = 25.01
        Money gross = Money.of("1000.55", Money.GBP);

        // When
        FactoringCalculationResult result = engine.calculate(gross, DEFAULT_FEE_PERCENT);

        // Then
        assertThat(result.factoringFee()).isEqualTo(Money.of("25.01", Money.GBP));
        assertThat(result.netPayoutAmount()).isEqualTo(Money.of("975.54", Money.GBP));
        assertThat(result.grossAmount()).isEqualTo(result.netPayoutAmount().plus(result.factoringFee()));
    }

    @ParameterizedTest
    @CsvSource({
            "500.00, 2.5, 12.50, 487.50",
            "1234.56, 2.5, 30.86, 1203.70",
            "99.99, 2.5, 2.50, 97.49",
            "10.00, 2.5, 0.25, 9.75",
            "1.00, 2.5, 0.03, 0.97"
    })
    @DisplayName("Parametric calculation preserving zero-sum balance invariant across values")
    void shouldPreserveBalanceInvariantAcrossVariousGrossAmounts(
            String grossStr, String rateStr, String expectedFeeStr, String expectedNetStr) {
        Money gross = Money.of(grossStr, Money.GBP);
        BigDecimal rate = new BigDecimal(rateStr);

        FactoringCalculationResult result = engine.calculate(gross, rate);

        assertThat(result.factoringFee()).isEqualTo(Money.of(expectedFeeStr, Money.GBP));
        assertThat(result.netPayoutAmount()).isEqualTo(Money.of(expectedNetStr, Money.GBP));
        assertThat(result.grossAmount()).isEqualTo(result.netPayoutAmount().plus(result.factoringFee()));
    }

    @Test
    @DisplayName("Multi-currency support: EUR and USD")
    void shouldSupportEurAndUsdFactoringCalculations() {
        Money eurGross = Money.of("2000.00", Money.EUR);
        FactoringCalculationResult eurResult = engine.calculate(eurGross, DEFAULT_FEE_PERCENT);
        assertThat(eurResult.factoringFee()).isEqualTo(Money.of("50.00", Money.EUR));
        assertThat(eurResult.netPayoutAmount()).isEqualTo(Money.of("1950.00", Money.EUR));

        Money usdGross = Money.of("3000.00", Money.USD);
        FactoringCalculationResult usdResult = engine.calculate(usdGross, DEFAULT_FEE_PERCENT);
        assertThat(usdResult.factoringFee()).isEqualTo(Money.of("75.00", Money.USD));
        assertThat(usdResult.netPayoutAmount()).isEqualTo(Money.of("2925.00", Money.USD));
    }

    @Test
    @DisplayName("Rejects non-positive gross amounts")
    void shouldRejectZeroOrNegativeGrossAmount() {
        assertThatThrownBy(() -> engine.calculate(Money.zero(Money.GBP), DEFAULT_FEE_PERCENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> engine.calculate(Money.of("-100.00", Money.GBP), DEFAULT_FEE_PERCENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    @DisplayName("Anti-collusion verification succeeds when shipper and carrier IDs differ")
    void shouldPassAntiCollusionWhenShipperAndCarrierDiffer() {
        ShipperId shipper = ShipperId.generate();
        CarrierId carrier = CarrierId.generate();

        assertThat(engine.verifyAntiCollusion(shipper, carrier)).isTrue();
    }

    @Test
    @DisplayName("Anti-collusion verification fails when shipper and carrier IDs are identical")
    void shouldFailAntiCollusionWhenShipperAndCarrierAreIdentical() {
        String sharedId = "0191c7a2-9b24-7f11-9a1c-3d842b10a512";
        ShipperId shipper = ShipperId.of(sharedId);
        CarrierId carrier = CarrierId.of(sharedId);

        assertThat(engine.verifyAntiCollusion(shipper, carrier)).isFalse();
    }

    @Test
    @DisplayName("Daily advance limit validation: below and above £50,000 threshold")
    void shouldVerifyDailyAdvanceLimitThreshold() {
        long capPence = 5000000L; // £50,000 in pence

        Money withinLimit = Money.ofMinor(4999900L, Money.GBP); // £49,999.00
        assertThat(engine.verifyDailyAdvanceLimit(withinLimit, capPence)).isTrue();

        Money exactLimit = Money.ofMinor(5000000L, Money.GBP); // £50,000.00
        assertThat(engine.verifyDailyAdvanceLimit(exactLimit, capPence)).isTrue();

        Money exceededLimit = Money.ofMinor(5000001L, Money.GBP); // £50,000.01
        assertThat(engine.verifyDailyAdvanceLimit(exceededLimit, capPence)).isFalse();
    }
}
