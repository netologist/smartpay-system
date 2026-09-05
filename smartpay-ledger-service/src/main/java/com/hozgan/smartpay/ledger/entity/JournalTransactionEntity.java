package com.hozgan.smartpay.ledger.entity;

import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_transactions")
@Getter
@Setter
@ToString
public class JournalTransactionEntity {

    @Id
    private UUID id;

    @Column(name = "reference_type", nullable = false, length = 32)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private JournalStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt = Instant.now();

    public JournalTransactionEntity() {
        this.id = UuidV7.generate();
    }

    public JournalTransactionEntity(String referenceType, String referenceId, String idempotencyKey, JournalStatus status, String description) {
        this.id = UuidV7.generate();
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.description = description;
        this.postedAt = Instant.now();
    }
}
