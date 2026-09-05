# ADR-003: Immutable Double-Entry Zero-Sum Ledger Architecture

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
Simple billing systems often manage balances by mutating a single column in an accounts table (`UPDATE accounts SET balance = balance - 100`). In a financial logistics and payment platform, this pattern is unacceptable because:
1. It destroys provenance: there is no immutable audit trail proving where funds originated and where they were transferred.
2. System crashes or software defects result in undetectable financial drift or lost funds.
3. Historical financial reporting and automated bank reconciliation become impossible.

## Decision
Enforce a strict **Double-Entry General Ledger** based on Luca Pacioli's five-century-old accounting principles:
1. **Zero-Sum Validation Invariant**: Every financial transaction (`journal_transactions`) must consist of paired debit and credit lines (`journal_entries`) where total debits equal total credits:
   $$\sum \text{Debit} = \sum \text{Credit}$$
   Any unbalanced transaction is rejected at the domain boundary with an `UnbalancedJournalTransactionException`.
2. **Append-Only Immutability**: Journal entries cannot be updated or deleted. A database trigger (`trg_prevent_ledger_modification`) strictly rejects `UPDATE` and `DELETE` operations at the database engine level. Erroneous postings must be corrected via compensating reversal transactions.
3. **Materialized Balances Under Pessimistic Locking**: An `account_balances` table is maintained as an optimistic/pessimistic cache synchronized atomically within the same transaction to serve low-latency balance queries.

## Consequences
* **Positive**: 100% financial auditability, mathematically provable balance integrity, deterministic bank statement reconciliation.
* **Negative**: Higher storage growth relative to in-place column updates (managed via standard PostgreSQL table partitioning).
