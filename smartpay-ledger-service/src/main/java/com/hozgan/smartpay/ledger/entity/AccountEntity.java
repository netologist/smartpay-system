package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.model.enums.EntityType;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@ToString
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "account_number", unique = true, nullable = false, length = 32)
    private String accountNumber;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 24)
    private EntityType entityType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AccountEntity() {
        this.id = UuidV7.generate();
    }

    public AccountEntity(String accountNumber, UUID entityId, EntityType entityType, String currency) {
        this.id = UuidV7.generate();
        this.accountNumber = accountNumber;
        this.entityId = entityId;
        this.entityType = entityType;
        this.currency = currency != null ? currency : "GBP";
        this.createdAt = Instant.now();
    }
    public com.hozgan.smartpay.common.model.id.AccountId getAccountId() {
        return com.hozgan.smartpay.common.model.id.AccountId.of(id);
    }

    public com.hozgan.smartpay.common.model.id.AccountNumber getAccountNumberTyped() {
        return com.hozgan.smartpay.common.model.id.AccountNumber.of(accountNumber);
    }
}
