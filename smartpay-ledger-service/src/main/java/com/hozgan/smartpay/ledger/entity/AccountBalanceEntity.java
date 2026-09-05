package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
public class AccountBalanceEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "cleared_balance_pence", nullable = false)
    private long clearedBalancePence = 0L;

    @Column(name = "hold_balance_pence", nullable = false)
    private long holdBalancePence = 0L;

    @Version
    @Column(name = "version", nullable = false)
    private long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AccountBalanceEntity() {
    }

    public AccountBalanceEntity(UUID accountId, long clearedBalancePence, long holdBalancePence) {
        this.accountId = accountId;
        this.clearedBalancePence = clearedBalancePence;
        this.holdBalancePence = holdBalancePence;
        this.version = 0L;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public long getClearedBalancePence() {
        return clearedBalancePence;
    }

    public void setClearedBalancePence(long clearedBalancePence) {
        this.clearedBalancePence = clearedBalancePence;
    }

    public long getHoldBalancePence() {
        return holdBalancePence;
    }

    public void setHoldBalancePence(long holdBalancePence) {
        this.holdBalancePence = holdBalancePence;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Money getClearedBalance(Currency currency) {
        return Money.ofMinor(clearedBalancePence, currency);
    }

    public void setClearedBalance(Money money) {
        this.clearedBalancePence = money.toMinorUnits();
    }

    public Money getHoldBalance(Currency currency) {
        return Money.ofMinor(holdBalancePence, currency);
    }

    public void setHoldBalance(Money money) {
        this.holdBalancePence = money.toMinorUnits();
    }

    public long getAvailableBalancePence() {
        return clearedBalancePence - holdBalancePence;
    }

    public Money getAvailableBalance(Currency currency) {
        return Money.ofMinor(getAvailableBalancePence(), currency);
    }
}
