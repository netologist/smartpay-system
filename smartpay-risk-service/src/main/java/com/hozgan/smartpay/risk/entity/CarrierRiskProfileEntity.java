package com.hozgan.smartpay.risk.entity;

import com.hozgan.smartpay.common.model.Money;
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
@Table(name = "carrier_risk_profiles")
public class CarrierRiskProfileEntity {

    @Id
    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CarrierProfileStatus status = CarrierProfileStatus.ACTIVE;

    @Column(name = "max_credit_limit_pence", nullable = false)
    private long maxCreditLimitPence = 0;

    @Column(name = "current_active_factoring_pence", nullable = false)
    private long currentActiveFactoringPence = 0;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "base_risk_score", nullable = false)
    private int baseRiskScore = 18;

    @Column(name = "historical_default_count", nullable = false)
    private int historicalDefaultCount = 0;

    @Column(name = "bank_account_number", length = 32)
    private String bankAccountNumber;

    @Column(name = "bank_sort_code", length = 16)
    private String bankSortCode;

    @Column(name = "last_known_ip", length = 64)
    private String lastKnownIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CarrierRiskProfileEntity() {
    }

    public CarrierRiskProfileEntity(UUID carrierId,
                                    CarrierProfileStatus status,
                                    long maxCreditLimitPence,
                                    long currentActiveFactoringPence,
                                    String currency,
                                    int baseRiskScore,
                                    int historicalDefaultCount,
                                    String bankAccountNumber,
                                    String bankSortCode,
                                    String lastKnownIp) {
        this.carrierId = carrierId;
        this.status = status;
        this.maxCreditLimitPence = maxCreditLimitPence;
        this.currentActiveFactoringPence = currentActiveFactoringPence;
        this.currency = currency;
        this.baseRiskScore = baseRiskScore;
        this.historicalDefaultCount = historicalDefaultCount;
        this.bankAccountNumber = bankAccountNumber;
        this.bankSortCode = bankSortCode;
        this.lastKnownIp = lastKnownIp;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isSanctionedOrBlacklisted() {
        return status == CarrierProfileStatus.BLACKLISTED || status == CarrierProfileStatus.SANCTIONED;
    }

    public boolean canAccommodateExposure(long requestedPence) {
        return (currentActiveFactoringPence + requestedPence) <= maxCreditLimitPence;
    }

    public void addActiveExposure(long requestedPence) {
        this.currentActiveFactoringPence += requestedPence;
        this.updatedAt = Instant.now();
    }

    public Money getMaxCreditLimit() {
        return Money.ofMinor(maxCreditLimitPence, Currency.getInstance(currency));
    }

    public Money getCurrentActiveFactoring() {
        return Money.ofMinor(currentActiveFactoringPence, Currency.getInstance(currency));
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(UUID carrierId) {
        this.carrierId = carrierId;
    }

    public CarrierProfileStatus getStatus() {
        return status;
    }

    public void setStatus(CarrierProfileStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public long getMaxCreditLimitPence() {
        return maxCreditLimitPence;
    }

    public void setMaxCreditLimitPence(long maxCreditLimitPence) {
        this.maxCreditLimitPence = maxCreditLimitPence;
    }

    public long getCurrentActiveFactoringPence() {
        return currentActiveFactoringPence;
    }

    public void setCurrentActiveFactoringPence(long currentActiveFactoringPence) {
        this.currentActiveFactoringPence = currentActiveFactoringPence;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getBaseRiskScore() {
        return baseRiskScore;
    }

    public void setBaseRiskScore(int baseRiskScore) {
        this.baseRiskScore = baseRiskScore;
    }

    public int getHistoricalDefaultCount() {
        return historicalDefaultCount;
    }

    public void setHistoricalDefaultCount(int historicalDefaultCount) {
        this.historicalDefaultCount = historicalDefaultCount;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankSortCode() {
        return bankSortCode;
    }

    public void setBankSortCode(String bankSortCode) {
        this.bankSortCode = bankSortCode;
    }

    public String getLastKnownIp() {
        return lastKnownIp;
    }

    public void setLastKnownIp(String lastKnownIp) {
        this.lastKnownIp = lastKnownIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
