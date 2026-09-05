package com.hozgan.smartpay.gateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    public IdempotencyRecordId() {
    }

    public IdempotencyRecordId(String tenantId, String idempotencyKey) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyRecordId that = (IdempotencyRecordId) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, idempotencyKey);
    }
}
