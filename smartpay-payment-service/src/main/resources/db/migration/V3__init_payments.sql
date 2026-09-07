-- ==============================================================================
-- Flyway Migration: V3__init_payments.sql
-- Domain: Payment records — source of truth for payment lifecycle state
-- Database: PostgreSQL 16
-- ==============================================================================

CREATE TABLE payments (
    id               UUID         NOT NULL PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    account_id       VARCHAR(64)  NOT NULL,
    amount_minor     BIGINT       NOT NULL,               -- minor currency units (e.g. cents)
    currency         VARCHAR(3)   NOT NULL,               -- ISO 4217
    status           VARCHAR(16)  NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    end_to_end_id    VARCHAR(128),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_status CHECK (status IN ('INITIATED', 'PROCESSING', 'SETTLED', 'FAILED')),
    CONSTRAINT uq_payment_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_payments_tenant_status ON payments (tenant_id, status);
CREATE INDEX idx_payments_account ON payments (account_id);
