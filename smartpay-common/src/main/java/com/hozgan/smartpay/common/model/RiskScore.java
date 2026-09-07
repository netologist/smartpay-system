package com.hozgan.smartpay.common.model;

import com.hozgan.smartpay.common.model.enums.RiskTier;

import java.util.Objects;

/**
 * Immutable value object representing a composite credit & fraud risk score between 0 and 100.
 */
public record RiskScore(int value) implements Comparable<RiskScore> {

    public RiskScore {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100 inclusive, got: " + value);
        }
    }

    public static RiskScore of(int value) {
        return new RiskScore(value);
    }

    public static RiskScore zero() {
        return new RiskScore(0);
    }

    public static RiskScore max() {
        return new RiskScore(100);
    }

    public RiskTier tier() {
        return RiskTier.fromScore(value);
    }

    public boolean isAcceptable() {
        return tier().isApproved();
    }

    @Override
    public int compareTo(RiskScore other) {
        Objects.requireNonNull(other, "other RiskScore cannot be null");
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
