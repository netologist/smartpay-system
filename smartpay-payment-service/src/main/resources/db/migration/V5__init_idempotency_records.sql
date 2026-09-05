-- ==============================================================================
-- Flyway Migration: V5__init_idempotency_records.sql
-- Domain: Distributed Two-Tier Idempotency & Tamper Detection
-- Database: PostgreSQL 16
-- ==============================================================================

CREATE TABLE idempotency_records (
    tenant_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL, -- SHA-256 fingerprint of request body
    status VARCHAR(16) NOT NULL,
    response_code INT NULL,
    response_body JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT chk_idemp_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Index for automated background TTL purging
CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);
