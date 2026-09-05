package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(name = "amount_in_pence", nullable = false)
    private long amountInPence;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public JournalEntryEntity() {
        this.id = UuidV7.generate();
    }

    public JournalEntryEntity(UUID transactionId, UUID accountId, EntryType entryType, long amountInPence, String currency) {
        this.id = UuidV7.generate();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amountInPence = amountInPence;
        this.currency = currency != null ? currency : "GBP";
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public long getAmountInPence() {
        return amountInPence;
    }

    public void setAmountInPence(long amountInPence) {
        this.amountInPence = amountInPence;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Money getAmount() {
        return Money.ofMinor(amountInPence, Currency.getInstance(currency));
    }

    public void setAmount(Money money) {
        this.amountInPence = money.toMinorUnits();
        this.currency = money.currency().getCurrencyCode();
    }
}
