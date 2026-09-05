package com.hozgan.smartpay.recon.entity;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_statement_lines")
public class BankStatementLineEntity {

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "statement_reference", nullable = false, length = 64)
    private String statementReference;

    @Column(name = "end_to_end_id", nullable = false, length = 64)
    private String endToEndId;

    @Column(name = "amount_in_pence", nullable = false)
    private long amountInPence;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 16)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;

    @Column(name = "matched_entry_id")
    private UUID matchedEntryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BankStatementLineEntity() {
        this.id = UuidV7.generate();
    }

    public BankStatementLineEntity(UUID statementId, String statementReference, String endToEndId, Money amount, EntryType entryType, LocalDate bookingDate) {
        this.id = UuidV7.generate();
        this.statementId = statementId;
        this.statementReference = statementReference;
        this.endToEndId = endToEndId;
        this.amountInPence = amount.toMinorUnits();
        this.entryType = entryType;
        this.bookingDate = bookingDate;
        this.reconciliationStatus = ReconciliationStatus.UNMATCHED;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStatementId() {
        return statementId;
    }

    public void setStatementId(UUID statementId) {
        this.statementId = statementId;
    }

    public String getStatementReference() {
        return statementReference;
    }

    public void setStatementReference(String statementReference) {
        this.statementReference = statementReference;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public long getAmountInPence() {
        return amountInPence;
    }

    public void setAmountInPence(long amountInPence) {
        this.amountInPence = amountInPence;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public ReconciliationStatus getReconciliationStatus() {
        return reconciliationStatus;
    }

    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) {
        this.reconciliationStatus = reconciliationStatus;
    }

    public UUID getMatchedEntryId() {
        return matchedEntryId;
    }

    public void setMatchedEntryId(UUID matchedEntryId) {
        this.matchedEntryId = matchedEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
