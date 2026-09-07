-- ==============================================================================
-- Flyway Migration: V6__init_bank_reconciliation.sql
-- Domain: ISO-20022 CAMT.053 & MT940 Bank Statement Auto-Reconciliation
-- Database: PostgreSQL 16
-- ==============================================================================

-- 1. Bank Statements Header
CREATE TABLE bank_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_reference VARCHAR(64) UNIQUE NOT NULL,
    bank_name VARCHAR(64) NOT NULL, -- e.g. 'ClearBank', 'Modulr', 'Barclays'
    account_number VARCHAR(32) NOT NULL,
    statement_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    opening_balance_pence BIGINT NOT NULL,
    closing_balance_pence BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_bank_statements_date ON bank_statements(statement_date DESC);

-- 2. Bank Statement Lines (CAMT.053 XML / MT940 line items)
CREATE TABLE bank_statement_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id UUID NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    statement_reference VARCHAR(64) NOT NULL,
    end_to_end_id VARCHAR(64) NOT NULL, -- Core matching key against Faster Payments transfer reference
    amount_in_pence BIGINT NOT NULL,
    entry_type VARCHAR(6) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    booking_date DATE NOT NULL,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED',
    matched_entry_id UUID NULL,  -- ledger transaction UUID; no FK (cross-service boundary)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_stmt_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_stmt_recon_status CHECK (
        reconciliation_status IN ('UNMATCHED', 'MATCHED', 'DISCREPANCY', 'MANUALLY_ADJUSTED')
    )
);

-- Fast lookup compound index for Two-Way Hash Matching Algorithm
CREATE INDEX idx_statement_matching ON bank_statement_lines(end_to_end_id, amount_in_pence);
CREATE INDEX idx_statement_status ON bank_statement_lines(reconciliation_status);
