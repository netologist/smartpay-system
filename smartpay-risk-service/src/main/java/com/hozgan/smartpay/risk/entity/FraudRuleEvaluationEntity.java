package com.hozgan.smartpay.risk.entity;

import com.hozgan.smartpay.common.model.enums.RiskTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_rule_evaluations")
public class FraudRuleEvaluationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "invoice_amount_pence", nullable = false)
    private long invoiceAmountPence;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier", nullable = false, length = 16)
    private RiskTier riskTier;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Column(name = "reasoning", nullable = false, length = 512)
    private String reasoning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_triggered", columnDefinition = "JSONB")
    private String rulesTriggered;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt = Instant.now();

    public FraudRuleEvaluationEntity() {
    }

    public FraudRuleEvaluationEntity(UUID id,
                                     UUID carrierId,
                                     UUID shipperId,
                                     long invoiceAmountPence,
                                     String currency,
                                     int riskScore,
                                     RiskTier riskTier,
                                     boolean approved,
                                     String reasoning,
                                     String rulesTriggered) {
        this.id = id;
        this.carrierId = carrierId;
        this.shipperId = shipperId;
        this.invoiceAmountPence = invoiceAmountPence;
        this.currency = currency;
        this.riskScore = riskScore;
        this.riskTier = riskTier;
        this.approved = approved;
        this.reasoning = reasoning;
        this.rulesTriggered = rulesTriggered;
        this.evaluatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(UUID carrierId) {
        this.carrierId = carrierId;
    }

    public UUID getShipperId() {
        return shipperId;
    }

    public void setShipperId(UUID shipperId) {
        this.shipperId = shipperId;
    }

    public long getInvoiceAmountPence() {
        return invoiceAmountPence;
    }

    public void setInvoiceAmountPence(long invoiceAmountPence) {
        this.invoiceAmountPence = invoiceAmountPence;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public RiskTier getRiskTier() {
        return riskTier;
    }

    public void setRiskTier(RiskTier riskTier) {
        this.riskTier = riskTier;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getRulesTriggered() {
        return rulesTriggered;
    }

    public void setRulesTriggered(String rulesTriggered) {
        this.rulesTriggered = rulesTriggered;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
