package com.hozgan.smartpay.common.model;

import org.jspecify.annotations.NonNull;

import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    private static final int DEFAULT_DIGITS = 2;

    public static final Currency GBP = Currency.getInstance("GBP");
    public static final Currency EUR = Currency.getInstance("EUR");
    public static final Currency USD = Currency.getInstance("USD");

    public static final RoundingMode RETAIL_ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");

        amount = amount.setScale(scaleOf(currency), RETAIL_ROUNDING);
    }

    public static Money of(String amount, Currency currency) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(long amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(double amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(String amount, String currency) {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, Currency.getInstance(currency));
    }

    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currency));
    }

    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance(currency));
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        Objects.requireNonNull(currency, "currency cannot be null");
        return new Money(BigDecimal.valueOf(minorUnits, scaleOf(currency)), currency);
    }

    public static Money ofGBP(String amount) {
        return of(amount, GBP);
    }

    public static Money ofGBP(BigDecimal amount) {
        return of(amount, GBP);
    }

    public static Money ofEUR(String amount) {
        return of(amount, EUR);
    }

    public static Money ofEUR(BigDecimal amount) {
        return of(amount, EUR);
    }

    public static Money ofUSD(String amount) {
        return of(amount, USD);
    }

    public static Money ofUSD(BigDecimal amount) {
        return of(amount, USD);
    }

    public static Money zero(Currency currency) {
        Objects.requireNonNull(currency, "currency cannot be null");
        return new Money(BigDecimal.ZERO, currency);
    }

    private static int scaleOf(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        return digits >= 0 ? digits : DEFAULT_DIGITS;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money plus(BigDecimal addend) {
        Objects.requireNonNull(addend, "addend cannot be null");
        return new Money(amount.add(addend), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money minus(BigDecimal subtrahend) {
        Objects.requireNonNull(subtrahend, "subtrahend cannot be null");
        return new Money(amount.subtract(subtrahend), currency);
    }

    public Money times(Money other) {
        requireSameCurrency(other);
        return new Money(amount.multiply(other.amount), currency);
    }

    public Money times(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier cannot be null");
        return new Money(amount.multiply(multiplier), currency);
    }

    public Money times(long multiplier) {
        return times(BigDecimal.valueOf(multiplier));
    }

    public Money times(double multiplier) {
        return times(BigDecimal.valueOf(multiplier));
    }

    public Money divide(Money other) {
        requireSameCurrency(other);
        if (other.isZero()) {
            throw new ArithmeticException("Division by zero");
        }
        return new Money(amount.divide(other.amount, scaleOf(currency), RETAIL_ROUNDING), currency);
    }

    public Money divide(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor cannot be null");
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return new Money(amount.divide(divisor, scaleOf(currency), RETAIL_ROUNDING), currency);
    }

    public Money divide(long divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }

    public Money percent(Money other) {
        requireSameCurrency(other);
        return percent(other.amount);
    }

    public Money percent(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate cannot be null");
        BigDecimal calculated = amount.multiply(rate)
                .divide(BigDecimal.valueOf(100), scaleOf(currency), RETAIL_ROUNDING);
        return new Money(calculated, currency);
    }

    public Money percent(double rate) {
        return percent(BigDecimal.valueOf(rate));
    }

    public Money percent(long rate) {
        return percent(BigDecimal.valueOf(rate));
    }

    public Money min(Money other) {
        requireSameCurrency(other);
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        requireSameCurrency(other);
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean atLeast(Money other) {
        requireSameCurrency(other);
        return compareTo(other) >= 0;
    }

    public boolean atMost(Money other) {
        requireSameCurrency(other);
        return compareTo(other) <= 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return compareTo(other) < 0;
    }

    public Money abs() {
        return isNegative() ? negate() : this;
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public long toMinorUnits() {
        return amount.movePointRight(scaleOf(currency)).longValueExact();
    }

    public MonetaryAmount toMonetaryAmount() {
        return org.javamoney.moneta.Money.of(amount, currency.getCurrencyCode());
    }

    public static Money from(MonetaryAmount monetaryAmount) {
        Objects.requireNonNull(monetaryAmount, "monetaryAmount cannot be null");
        BigDecimal number = monetaryAmount.getNumber().numberValue(BigDecimal.class);
        Currency cur = Currency.getInstance(monetaryAmount.getCurrency().getCurrencyCode());
        return new Money(number, cur);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money cannot be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public int compareTo(@NonNull Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount.toPlainString();
    }
}
