package com.hozgan.smartpay.common.model;

import com.hozgan.smartpay.common.model.id.AccountId;

import java.util.Objects;

public record AccountBalance(
        AccountId accountId,
        Money clearedBalance,
        Money holdBalance,
        long version
) {

    public AccountBalance {
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(clearedBalance, "clearedBalance cannot be null");
        Objects.requireNonNull(holdBalance, "holdBalance cannot be null");

        if (clearedBalance.isNegative()) {
            throw new IllegalArgumentException("clearedBalance cannot be negative: " + clearedBalance);
        }
        if (holdBalance.isNegative()) {
            throw new IllegalArgumentException("holdBalance cannot be negative: " + holdBalance);
        }
        if (!clearedBalance.currency().equals(holdBalance.currency())) {
            throw new IllegalArgumentException("clearedBalance and holdBalance currencies must match");
        }
    }

    public Money availableBalance() {
        return clearedBalance.minus(holdBalance);
    }

    public boolean canCover(Money amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        return availableBalance().atLeast(amount);
    }
}
