package com.hozgan.smartpay.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;

@Tag("unit")
@DisplayName("Money Value Object Tests")
class MoneyTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency BHD = Currency.getInstance("BHD");

    @Nested
    @DisplayName("Factory & Constructor Tests")
    class FactoryAndConstructorTests {

        @Test
        @DisplayName("Zero GBP constant is safe, non-null, and zero amount")
        void zeroGBPConstantIsSafe() {
            Money zero = Money.zero(GBP);
            assertThat(zero).isNotNull();
            assertThat(zero.amount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.currency()).isEqualTo(GBP);
            assertThat(zero.amount().scale()).isEqualTo(2);
            assertThat(zero.isZero()).isTrue();
            assertThat(zero.isPositive()).isFalse();
            assertThat(zero.isNegative()).isFalse();
        }

        @Test
        @DisplayName("Zero with zero-decimal currency (JPY) has scale 0")
        void zeroJPYHasScaleZero() {
            Money zeroJPY = Money.zero(JPY);
            assertThat(zeroJPY.amount().scale()).isZero();
            assertThat(zeroJPY.amount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Null amount or null currency throws NullPointerException")
        void nullInputsThrowNpe() {
            assertThatThrownBy(() -> new Money(null, GBP))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("amount cannot be null");

            assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("currency cannot be null");

            assertThatThrownBy(() -> Money.of((String) null, GBP))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> Money.zero(null))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> Money.ofMinor(100, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("String factory creates properly scaled Money")
        void stringFactoryCreatesProperMoney() {
            Money m = Money.of("125.75", GBP);
            assertThat(m.amount()).isEqualTo(new BigDecimal("125.75"));
            assertThat(m.currency()).isEqualTo(GBP);
        }

        @Test
        @DisplayName("Long and double factories create properly scaled Money")
        void numericFactoriesCreateProperMoney() {
            Money fromLong = Money.of(50L, GBP);
            assertThat(fromLong.amount()).isEqualTo(new BigDecimal("50.00"));

            Money fromDouble = Money.of(19.99, EUR);
            assertThat(fromDouble.amount()).isEqualTo(new BigDecimal("19.99"));
        }

        @Test
        @DisplayName("Currency specific shortcut factories work as expected")
        void currencySpecificShortcuts() {
            assertThat(Money.ofGBP("10.00")).isEqualTo(Money.of("10.00", GBP));
            assertThat(Money.ofGBP(new BigDecimal("10.00"))).isEqualTo(Money.of("10.00", GBP));
            assertThat(Money.ofGBP(new BigDecimal("10.00"))).isEqualTo(Money.of("10.00", "GBP"));
            assertThat(Money.ofEUR("20.00")).isEqualTo(Money.of("20.00", EUR));
            assertThat(Money.ofEUR(new BigDecimal("20.00"))).isEqualTo(Money.of("20.00", EUR));
            assertThat(Money.ofEUR(new BigDecimal("20.00"))).isEqualTo(Money.of("20.00", "EUR"));
            assertThat(Money.ofUSD("30.00")).isEqualTo(Money.of("30.00", USD));
            assertThat(Money.ofUSD(new BigDecimal("30.00"))).isEqualTo(Money.of("30.00", USD));
            assertThat(Money.ofUSD(new BigDecimal("30.00"))).isEqualTo(Money.of("30.00", "USD"));
        }

        @Test
        @DisplayName("Scale normalization: different input scales produce equal records")
        void scaleNormalizationEnsuresRecordEquality() {
            Money m1 = Money.of("10", GBP);
            Money m2 = Money.of("10.00", GBP);
            Money m3 = new Money(new BigDecimal("10.0000"), GBP);

            assertThat(m1).isEqualTo(m2);
            assertThat(m2).isEqualTo(m3);
            assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
            assertThat(m1.amount().scale()).isEqualTo(2);
            assertThat(m2.amount().scale()).isEqualTo(2);
            assertThat(m3.amount().scale()).isEqualTo(2);

            Money zeroFromStr = Money.of("0", GBP);
            Money zeroFromFactory = Money.zero(GBP);
            assertThat(zeroFromStr).isEqualTo(zeroFromFactory);
        }

        @ParameterizedTest(name = "Rounding {0} GBP with HALF_UP -> {1}")
        @CsvSource({
                "10.555, 10.56",
                "10.554, 10.55",
                "10.550, 10.55",
                "10.505, 10.51",
                "10.5, 10.50"
        })
        void roundingHalfUpAppliedCorrectly(String input, String expected) {
            Money money = Money.of(input, GBP);
            assertThat(money.amount()).isEqualTo(new BigDecimal(expected));
        }

        @Test
        @DisplayName("JPY has 0 scale, BHD has 3 scale")
        void specificCurrencyScales() {
            Money jpy = Money.of("100.6", JPY);
            assertThat(jpy.amount()).isEqualTo(new BigDecimal("101"));
            assertThat(jpy.amount().scale()).isZero();

            Money bhd = Money.of("1.2345", BHD);
            assertThat(bhd.amount()).isEqualTo(new BigDecimal("1.235"));
            assertThat(bhd.amount().scale()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Arithmetic Operations Tests")
    class ArithmeticTests {

        @Test
        @DisplayName("plus adds amounts with same currency")
        void plusAddsAmounts() {
            Money m1 = Money.of("10.50", GBP);
            Money m2 = Money.of("4.25", GBP);
            Money result = m1.plus(m2);

            assertThat(result).isEqualTo(Money.of("14.75", GBP));
            assertThat(m1.plus(new BigDecimal("5.00"))).isEqualTo(Money.of("15.50", GBP));
        }

        @Test
        @DisplayName("plus throws IllegalArgumentException on currency mismatch")
        void plusThrowsOnCurrencyMismatch() {
            Money gbp = Money.of("10.00", GBP);
            Money eur = Money.of("10.00", EUR);

            assertThatThrownBy(() -> gbp.plus(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("currency mismatch");
        }

        @Test
        @DisplayName("minus subtracts amounts with same currency")
        void minusSubtractsAmounts() {
            Money m1 = Money.of("10.50", GBP);
            Money m2 = Money.of("4.25", GBP);
            Money result = m1.minus(m2);

            assertThat(result).isEqualTo(Money.of("6.25", GBP));
            assertThat(m1.minus(new BigDecimal("2.50"))).isEqualTo(Money.of("8.00", GBP));
        }

        @Test
        @DisplayName("minus can produce negative amount")
        void minusCanProduceNegative() {
            Money m1 = Money.of("5.00", GBP);
            Money m2 = Money.of("12.00", GBP);
            Money result = m1.minus(m2);

            assertThat(result).isEqualTo(Money.of("-7.00", GBP));
            assertThat(result.isNegative()).isTrue();
        }

        @Test
        @DisplayName("times multiplies by Money, BigDecimal, long, or double")
        void timesMultipliesAmounts() {
            Money base = Money.of("15.00", GBP);

            assertThat(base.times(Money.of("2.00", GBP))).isEqualTo(Money.of("30.00", GBP));
            assertThat(base.times(new BigDecimal("2.5"))).isEqualTo(Money.of("37.50", GBP));
            assertThat(base.times(3L)).isEqualTo(Money.of("45.00", GBP));
            assertThat(base.times(1.5)).isEqualTo(Money.of("22.50", GBP));
        }

        @Test
        @DisplayName("divide divides by Money, BigDecimal, and long with HALF_UP rounding")
        void divideAmounts() {
            Money base = Money.of("100.00", GBP);

            assertThat(base.divide(Money.of("4.00", GBP))).isEqualTo(Money.of("25.00", GBP));
            assertThat(base.divide(new BigDecimal("3"))).isEqualTo(Money.of("33.33", GBP));
            assertThat(base.divide(2L)).isEqualTo(Money.of("50.00", GBP));
        }

        @Test
        @DisplayName("divide by zero throws ArithmeticException")
        void divideByZeroThrows() {
            Money base = Money.of("100.00", GBP);

            assertThatThrownBy(() -> base.divide(Money.zero(GBP)))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Division by zero");

            assertThatThrownBy(() -> base.divide(BigDecimal.ZERO))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Division by zero");

            assertThatThrownBy(() -> base.divide(0L))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Division by zero");
        }

        @Test
        @DisplayName("percent calculates percentage of Money correctly")
        void percentCalculatesCorrectly() {
            Money base = Money.of("200.00", GBP);

            assertThat(base.percent(new BigDecimal("20"))).isEqualTo(Money.of("40.00", GBP));
            assertThat(base.percent(17.5)).isEqualTo(Money.of("35.00", GBP));
            assertThat(base.percent(10L)).isEqualTo(Money.of("20.00", GBP));
            assertThat(base.percent(Money.of("25.00", GBP))).isEqualTo(Money.of("50.00", GBP));
        }
    }

    @Nested
    @DisplayName("Comparison & Ordering Tests")
    class ComparisonTests {

        @Test
        @DisplayName("compareTo orders properly and throws on currency mismatch")
        void compareToOrdersProperly() {
            Money low = Money.of("10.00", GBP);
            Money high = Money.of("20.00", GBP);
            Money equalLow = Money.of("10.00", GBP);

            assertThat(low.compareTo(high)).isNegative();
            assertThat(high.compareTo(low)).isPositive();
            assertThat(low.compareTo(equalLow)).isZero();

            Money eur = Money.of("10.00", EUR);
            assertThatThrownBy(() -> low.compareTo(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("currency mismatch");
        }

        @Test
        @DisplayName("min and max return correct instances")
        void minAndMaxWork() {
            Money m1 = Money.of("15.00", GBP);
            Money m2 = Money.of("25.00", GBP);

            assertThat(m1.min(m2)).isEqualTo(m1);
            assertThat(m2.min(m1)).isEqualTo(m1);

            assertThat(m1.max(m2)).isEqualTo(m2);
            assertThat(m2.max(m1)).isEqualTo(m2);
        }

        @Test
        @DisplayName("atLeast and atMost return boolean bounds check")
        void atLeastAndAtMost() {
            Money threshold = Money.of("50.00", GBP);

            assertThat(Money.of("60.00", GBP).atLeast(threshold)).isTrue();
            assertThat(Money.of("50.00", GBP).atLeast(threshold)).isTrue();
            assertThat(Money.of("40.00", GBP).atLeast(threshold)).isFalse();

            assertThat(Money.of("40.00", GBP).atMost(threshold)).isTrue();
            assertThat(Money.of("50.00", GBP).atMost(threshold)).isTrue();
            assertThat(Money.of("60.00", GBP).atMost(threshold)).isFalse();
        }

        @Test
        @DisplayName("isGreaterThan and isLessThan work as expected")
        void strictInequalities() {
            Money m10 = Money.of("10.00", GBP);
            Money m20 = Money.of("20.00", GBP);

            assertThat(m20.isGreaterThan(m10)).isTrue();
            assertThat(m10.isGreaterThan(m20)).isFalse();
            assertThat(m10.isGreaterThan(m10)).isFalse();

            assertThat(m10.isLessThan(m20)).isTrue();
            assertThat(m20.isLessThan(m10)).isFalse();
            assertThat(m10.isLessThan(m10)).isFalse();
        }
    }

    @Nested
    @DisplayName("Predicates, Unary & Minor Units Tests")
    class PredicateAndUnaryTests {

        @Test
        @DisplayName("isPositive, isNegative, and isZero behave correctly")
        void predicatesWork() {
            Money positive = Money.of("1.00", GBP);
            Money zero = Money.zero(GBP);
            Money negative = Money.of("-1.00", GBP);

            assertThat(positive.isPositive()).isTrue();
            assertThat(positive.isNegative()).isFalse();
            assertThat(positive.isZero()).isFalse();

            assertThat(zero.isPositive()).isFalse();
            assertThat(zero.isNegative()).isFalse();
            assertThat(zero.isZero()).isTrue();

            assertThat(negative.isPositive()).isFalse();
            assertThat(negative.isNegative()).isTrue();
            assertThat(negative.isZero()).isFalse();
        }

        @Test
        @DisplayName("abs and negate return correct Money")
        void absAndNegateWork() {
            Money negative = Money.of("-15.50", GBP);
            Money positive = Money.of("15.50", GBP);

            assertThat(negative.abs()).isEqualTo(positive);
            assertThat(positive.abs()).isEqualTo(positive);

            assertThat(positive.negate()).isEqualTo(negative);
            assertThat(negative.negate()).isEqualTo(positive);
        }

        @Test
        @DisplayName("toMinorUnits and ofMinor correctly handle cents and pence")
        void minorUnitsRoundTrip() {
            Money gbp = Money.of("12.34", GBP);
            assertThat(gbp.toMinorUnits()).isEqualTo(1234L);
            assertThat(Money.ofMinor(1234L, GBP)).isEqualTo(gbp);

            Money jpy = Money.of("500", JPY);
            assertThat(jpy.toMinorUnits()).isEqualTo(500L);
            assertThat(Money.ofMinor(500L, JPY)).isEqualTo(jpy);

            Money bhd = Money.of("1.250", BHD);
            assertThat(bhd.toMinorUnits()).isEqualTo(1250L);
            assertThat(Money.ofMinor(1250L, BHD)).isEqualTo(bhd);
        }

        @Test
        @DisplayName("toString provides clean formatted representation")
        void toStringFormat() {
            Money gbp = Money.of("99.99", GBP);
            assertThat(gbp.toString()).isEqualTo("GBP 99.99");
        }
    }

    @Nested
    @DisplayName("JavaMoney (Moneta / JSR 354) Interop Tests")
    class JavaMoneyInteropTests {

        @Test
        @DisplayName("toMonetaryAmount converts Money to javax.money.MonetaryAmount")
        void toMonetaryAmountConvertsProperly() {
            Money money = Money.of("42.50", GBP);
            MonetaryAmount monetaryAmount = money.toMonetaryAmount();

            assertThat(monetaryAmount).isNotNull();
            assertThat(monetaryAmount.getCurrency().getCurrencyCode()).isEqualTo("GBP");
            assertThat(monetaryAmount.getNumber().numberValue(BigDecimal.class))
                    .isEqualByComparingTo(new BigDecimal("42.50"));
        }

        @Test
        @DisplayName("from creates Money from MonetaryAmount with exact precision")
        void fromCreatesMoneyFromMonetaryAmount() {
            org.javamoney.moneta.Money monetaMoney = org.javamoney.moneta.Money.of(new BigDecimal("88.20"), "EUR");
            Money converted = Money.from(monetaMoney);

            assertThat(converted).isEqualTo(Money.of("88.20", EUR));
            assertThat(converted.currency()).isEqualTo(EUR);
        }

        @Test
        @DisplayName("Round-trip between Money and MonetaryAmount preserves value")
        void roundTripPreservesFidelity() {
            Money original = Money.of("199.95", USD);
            MonetaryAmount monetaryAmount = original.toMonetaryAmount();
            Money roundTrip = Money.from(monetaryAmount);

            assertThat(roundTrip).isEqualTo(original);
        }
    }
}
