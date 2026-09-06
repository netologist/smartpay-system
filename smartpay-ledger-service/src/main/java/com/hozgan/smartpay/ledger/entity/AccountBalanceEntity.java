package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
@Getter
@Setter
@NoArgsConstructor
@ToString
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

    public AccountBalanceEntity(UUID accountId, long clearedBalancePence, long holdBalancePence) {
        this.accountId = accountId;
        this.clearedBalancePence = clearedBalancePence;
        this.holdBalancePence = holdBalancePence;
        this.version = 0L;
        this.updatedAt = Instant.now();
    }

    public AccountBalanceEntity(com.hozgan.smartpay.common.model.id.AccountId accountId, long clearedBalancePence, long holdBalancePence) {
        this(accountId.value(), clearedBalancePence, holdBalancePence);
    }

    public com.hozgan.smartpay.common.model.id.AccountId getAccountIdTyped() {
        return com.hozgan.smartpay.common.model.id.AccountId.of(accountId);
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
