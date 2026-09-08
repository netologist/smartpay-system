#!/usr/bin/env bash
# ==============================================================================
# SmartPay Demo Data Seeder (Ephemeral KinD / Dev Environments)
# Seeds the ledger demo accounts referenced by the E2E smoke suite so that
# payment initiation -> ledger HoldFunds succeeds (HTTP 201).
# Idempotent: safe to re-run against an existing database.
# ==============================================================================

set -euo pipefail

NAMESPACE="${NAMESPACE:-smartpay}"
DB_USER="${DB_USER:-smartpay}"
DB_NAME="${DB_NAME:-smartpay_db}"

echo "Seeding demo ledger accounts for E2E smoke tests..."

kubectl exec -n "${NAMESPACE}" deployment/postgres -- psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 <<'SQL'
-- Debtor account used by e2e-smoke-test.sh (AC-1 happy path hold)
INSERT INTO ledger.accounts (id, account_number, entity_id, entity_type, currency)
VALUES ('0191c7a2-9b24-7f11-9a1c-3d842b10a512', 'ACC-SMOKE-512', '0191c7a2-9b24-7f11-9a1c-3d842b10a512', 'CARRIER', 'GBP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger.account_balances (account_id, cleared_balance_pence, hold_balance_pence)
VALUES ('0191c7a2-9b24-7f11-9a1c-3d842b10a512', 10000000, 0)
ON CONFLICT (account_id) DO NOTHING;

-- Creditor account referenced by the smoke payload
INSERT INTO ledger.accounts (id, account_number, entity_id, entity_type, currency)
VALUES ('0191c7a2-9b24-7f11-9a1c-3d842b10a513', 'ACC-SMOKE-513', '0191c7a2-9b24-7f11-9a1c-3d842b10a513', 'SHIPPER', 'GBP')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ledger.account_balances (account_id, cleared_balance_pence, hold_balance_pence)
VALUES ('0191c7a2-9b24-7f11-9a1c-3d842b10a513', 10000000, 0)
ON CONFLICT (account_id) DO NOTHING;
SQL

echo "✅ Demo ledger accounts seeded."
