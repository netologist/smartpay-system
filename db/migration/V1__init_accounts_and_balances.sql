-- ==============================================================================
-- Flyway Migration: V1__init_accounts_and_balances.sql
-- Domain: Accounts & Materialized Balance Cache with Pessimistic / Optimistic Locking
-- Database: PostgreSQL 16
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Accounts Table (Chart of Accounts)
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(32) UNIQUE NOT NULL,
    entity_id UUID NOT NULL, -- Shipper ID, Carrier ID, or Platform Entity ID
    entity_type VARCHAR(24) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_entity_type CHECK (
        entity_type IN ('SHIPPER', 'CARRIER', 'PLATFORM_ESCROW', 'PLATFORM_FEE', 'FACTORING_RESERVE')
    )
);

CREATE INDEX idx_accounts_entity ON accounts(entity_id, entity_type);
CREATE INDEX idx_accounts_number ON accounts(account_number);

-- 2. Materialized Account Balances (Optimistic Concurrency & Physical Balance Guard)
CREATE TABLE account_balances (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE RESTRICT,
    cleared_balance_pence BIGINT NOT NULL DEFAULT 0,
    hold_balance_pence BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_positive_cleared CHECK (cleared_balance_pence >= 0),
    CONSTRAINT chk_positive_hold CHECK (hold_balance_pence >= 0)
);

-- Trigger to automatically update updated_at on account_balances
CREATE OR REPLACE FUNCTION update_account_balance_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_balances_updated_at
BEFORE UPDATE ON account_balances
FOR EACH ROW
EXECUTE FUNCTION update_account_balance_timestamp();
