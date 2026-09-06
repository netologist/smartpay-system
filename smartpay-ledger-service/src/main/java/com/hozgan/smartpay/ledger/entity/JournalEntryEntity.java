package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.converter.AccountIdConverter;
import com.hozgan.smartpay.common.converter.TransactionIdConverter;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.TransactionId;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@ToString
public class JournalEntryEntity {

    @Id
    private UUID id;

    @Convert(converter = TransactionIdConverter.class)
    @Column(name = "transaction_id", nullable = false)
    private TransactionId transactionId;

    @Convert(converter = AccountIdConverter.class)
    @Column(name = "account_id", nullable = false)
    private AccountId accountId;

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

    public JournalEntryEntity(TransactionId transactionId, AccountId accountId, EntryType entryType, long amountInPence, String currency) {
        this.id = UuidV7.generate();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amountInPence = amountInPence;
        this.currency = currency != null ? currency : "GBP";
        this.createdAt = Instant.now();
    }

    public JournalEntryEntity(UUID transactionId, UUID accountId, EntryType entryType, long amountInPence, String currency) {
        this(TransactionId.of(transactionId), AccountId.of(accountId), entryType, amountInPence, currency);
    }

    public Money getAmount() {
        return Money.ofMinor(amountInPence, Currency.getInstance(currency));
    }

    public void setAmount(Money money) {
        this.amountInPence = money.toMinorUnits();
        this.currency = money.currency().getCurrencyCode();
    }
}
