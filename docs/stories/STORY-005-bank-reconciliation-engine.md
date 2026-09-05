# STORY-005: Bank Statement & Auto-Reconciliation Engine

## 📌 Overview
* **Target Module**: `smartpay-recon-service`
* **Priority**: P2 (Reconciliation & Financial Control)
* **Associated Database Tables**: `bank_statements`, `bank_statement_lines` (`V6`)
* **Associated Services**: `smartpay-ledger-service`
* **Required `smartpay-common` Components**:
  * `Money` (Statement balances and line item amounts)
  * `StatementReference`, `EndToEndId` (Strongly-typed IDs)
  * `EntryType`, `ReconciliationStatus` (`UNMATCHED`, `MATCHED`, `MANUALLY_ADJUSTED`)
  * `UnmatchedBankStatementException`

---

## 🎯 User Story
> **As a** Finance Operations Manager,  
> **I want to** ingest ISO-20022 CAMT.053 XML and MT940 bank statements from clearing banks (e.g. ClearBank, Barclays) and automatically reconcile each line against double-entry journal postings using `end_to_end_id`,  
> **So that** bank account cash positions match internal ledger balances and discrepancies are isolated within minutes.

---

## 📐 Architecture & Matching Rules

1. **Deterministic Matching Key**:
   * For Faster Payments and SEPA disbursements, the statement `EndToEndId` equals the payment reference:
     `bank_statement_lines.end_to_end_id == journal_transactions.idempotency_key` (or `reference_id`).

2. **Amount and Direction Invariants**:
   * A bank `CREDIT` (inflow) corresponds to a ledger account `CREDIT`.
   * Amounts must match to the exact minor unit (pence/cent): `bankAmount.equals(ledgerAmount)`.

---

## ✅ Acceptance Criteria (AC)

### AC-1: CAMT.053 XML Statement Ingestion
* **Given**: A valid ISO-20022 CAMT.053 XML file containing 100 statement lines,
* **When**: `importStatement(xmlStream)` is invoked,
* **Then**: Rows are persisted in `bank_statements` and `bank_statement_lines` with `reconciliation_status = UNMATCHED`.

### AC-2: Automated Matching Pipeline
* **Given**: An unmatched statement line of £500 with reference `E2E-998822`,
* **When**: The reconciliation matching algorithm runs,
* **Then**: The matching ledger transaction is retrieved via gRPC; if amount, currency, and direction match, the line transitions to `MATCHED` and updates `matched_entry_id`.

### AC-3: Discrepancy Detection & Alerting
* **Given**: A statement line with £500 that matches a ledger transaction of £490,
* **When**: Reconciliation executes,
* **Then**: The line remains `UNMATCHED` (or flags as `DISCREPANCY`), and an audit discrepancy report is generated.

---

## 💻 Class Implementation Structure

```
smartpay-recon-service/src/main/java/com/hozgan/smartpay/recon/
├── parser/
│   ├── StatementParser.java            // CAMT.053 XML / MT940 parser interface
│   └── impl/
│       └── Camt053XmlStatementParser.java
├── service/
│   ├── BankStatementService.java       // Statement ingestion and persistence
│   └── ReconciliationEngine.java       // EndToEndId matching algorithm
└── web/
    └── ReconciliationController.java   // POST /api/v1/recon/statements/upload
```
