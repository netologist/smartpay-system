package com.hozgan.smartpay.risk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipper_risk_profiles")
public class ShipperRiskProfileEntity {

    @Id
    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ShipperProfileStatus status = ShipperProfileStatus.ACTIVE;

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

    public ShipperRiskProfileEntity() {
    }

    public ShipperRiskProfileEntity(UUID shipperId,
                                    ShipperProfileStatus status,
                                    String bankAccountNumber,
                                    String bankSortCode,
                                    String lastKnownIp) {
        this.shipperId = shipperId;
        this.status = status;
        this.bankAccountNumber = bankAccountNumber;
        this.bankSortCode = bankSortCode;
        this.lastKnownIp = lastKnownIp;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isBlacklisted() {
        return status == ShipperProfileStatus.BLACKLISTED;
    }

    public UUID getShipperId() {
        return shipperId;
    }

    public void setShipperId(UUID shipperId) {
        this.shipperId = shipperId;
    }

    public ShipperProfileStatus getStatus() {
        return status;
    }

    public void setStatus(ShipperProfileStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
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
