package com.hozgan.smartpay.recon.entity;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bank_statements")
public class BankStatementEntity {

    @Id
    private UUID id;

    @Column(name = "statement_reference", unique = true, nullable = false, length = 64)
    private String statementReference;

    @Column(name = "bank_name", nullable = false, length = 64)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 32)
    private String accountNumber;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "opening_balance_pence", nullable = false)
    private long openingBalancePence;

    @Column(name = "closing_balance_pence", nullable = false)
    private long closingBalancePence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public BankStatementEntity() {
        this.id = UuidV7.generate();
    }

    public BankStatementEntity(String statementReference, String bankName, String accountNumber, LocalDate statementDate, Money openingBalance, Money closingBalance) {
        this.id = UuidV7.generate();
        this.statementReference = statementReference;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.statementDate = statementDate;
        this.openingBalancePence = openingBalance.toMinorUnits();
        this.closingBalancePence = closingBalance.toMinorUnits();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStatementReference() {
        return statementReference;
    }

    public void setStatementReference(String statementReference) {
        this.statementReference = statementReference;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public long getOpeningBalancePence() {
        return openingBalancePence;
    }

    public void setOpeningBalancePence(long openingBalancePence) {
        this.openingBalancePence = openingBalancePence;
    }

    public long getClosingBalancePence() {
        return closingBalancePence;
    }

    public void setClosingBalancePence(long closingBalancePence) {
        this.closingBalancePence = closingBalancePence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
