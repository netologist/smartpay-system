package com.hozgan.smartpay.common.model.enums;

/**
 * Risk tier classification based on composite risk score [0..100].
 */
public enum RiskTier {
    /** 0 - 25: Instant approval recommended (full advance rate ~97.5%). */
    LOW(0, 25, true, 0.975),

    /** 26 - 39: Approved with reduced advance rate (85%). */
    MEDIUM(26, 39, true, 0.850),

    /** 40 - 69: Automatic hold; requires compliance approval before disbursement. */
    HIGH(40, 69, false, 0.0),

    /** 70 - 100: Immediate rejection (fraud anomaly or sanction flag). */
    CRITICAL(70, 100, false, 0.0);

    private final int minScore;
    private final int maxScore;
    private final boolean approved;
    private final double advanceRate;

    RiskTier(int minScore, int maxScore, boolean approved, double advanceRate) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.approved = approved;
        this.advanceRate = advanceRate;
    }

    public int minScore() {
        return minScore;
    }

    public int maxScore() {
        return maxScore;
    }

    public boolean isApproved() {
        return approved;
    }

    public double advanceRate() {
        return advanceRate;
    }

    public static RiskTier fromScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100, got: " + score);
        }
        for (RiskTier tier : values()) {
            if (score >= tier.minScore && score <= tier.maxScore) {
                return tier;
            }
        }
        return CRITICAL;
    }
}
