-- ==============================================================================
-- Flyway Migration: V2__init_double_entry_ledger.sql
-- Domain: Immutable Double-Entry Bookkeeping & Zero-Sum Journal Entries
-- Database: PostgreSQL 16
-- ==============================================================================

-- 1. Journal Transactions (Transaction Header)
CREATE TABLE journal_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_type VARCHAR(32) NOT NULL, -- e.g. 'INVOICE_BATCH_SETTLEMENT', 'FACTORING_PAYOUT', 'REVERSAL'
    reference_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    status VARCHAR(16) NOT NULL,
    description TEXT,
    posted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'POSTED', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_journal_transactions_ref ON journal_transactions(reference_type, reference_id);
CREATE INDEX idx_journal_transactions_idemp ON journal_transactions(idempotency_key);

-- 2. Journal Entries (Immutable Append-Only Lines)
-- Invariant: For every transaction_id, SUM(DEBIT) must equal SUM(CREDIT)
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES journal_transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    entry_type VARCHAR(6) NOT NULL,
    amount_in_pence BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GBP',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_positive_amount CHECK (amount_in_pence > 0)
);

CREATE INDEX idx_journal_entries_account ON journal_entries(account_id, created_at DESC);
CREATE INDEX idx_journal_entries_tx ON journal_entries(transaction_id);

-- 3. Financial Immutability Trigger: Strictly PREVENT any UPDATE or DELETE on journal_entries
CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Financial Audit Violation: journal_entries is append-only. UPDATE or DELETE is strictly prohibited!';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entries_immutable
BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_modification();
