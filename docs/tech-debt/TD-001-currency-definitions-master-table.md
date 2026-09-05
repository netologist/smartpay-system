# Technical Debt Record: TD-001

## Title
**Centralized Currency Definitions (ISO-4217) Master Table & Temporal Validity**

## Status
**Recorded / Deferred** (Priority: Medium)

## Date
2026-09-05

## Context
In the SmartPay platform, monetary amounts are modeled as `Money(BigDecimal amount, Currency currency)` in application code and stored as minor units (`BIGINT` in pence/cents) with an associated ISO-4217 currency code (`VARCHAR(3) NOT NULL DEFAULT 'GBP'`) across:
- `accounts.currency` (`smartpay-ledger-service`)
- `journal_entries.currency` (`smartpay-ledger-service`)
- `invoices.currency` (`smartpay-invoice-service`)
- `bank_statements.currency` & `bank_statement_lines.currency` (`smartpay-recon-service`)

Currently, currencies are checked via Java's `java.util.Currency` and `com.hozgan.smartpay.common.model.Money`, with database tables having `VARCHAR(3)` fields without a relational foreign key to a centralized `currency_definitions` master table.

## Why Deferred?
1. **Microservice Database Decoupling**: Each service (`ledger`, `invoice`, `recon`) owns its independent PostgreSQL database. Enforcing foreign keys across databases would either require Postgres Foreign Data Wrappers (FDW) or copying/syncing the reference table in every service database.
2. **Current Scope**: Early phase operations primarily settle in `GBP`, with cross-border support for `EUR` and `USD`. All supported currencies have an exponent of 2 (100 minor units = 1 major unit), which `Money` handles uniformly.

## Target Architecture (When to Pay Debt)
When expanding to zero-decimal currencies (e.g. `JPY`, `KRW`) or 3-decimal currencies (e.g. `BHD`, `KWD`), or when implementing a dedicated Master Data Management (MDM) / Configuration Service, introduce a `currency_definitions` table:

```sql
CREATE TABLE currency_definitions (
    currency_code VARCHAR(3) PRIMARY KEY,      -- ISO-4217 alpha code (e.g. 'GBP', 'EUR')
    numeric_code VARCHAR(3) NOT NULL,          -- ISO-4217 numeric code (e.g. '826', '978')
    name VARCHAR(64) NOT NULL,                 -- e.g. 'Pound Sterling'
    symbol VARCHAR(8) NOT NULL,                -- e.g. '£', '€', '$'
    exponent INT NOT NULL DEFAULT 2,           -- Decimal fraction digits (0, 2, 3)
    is_active BOOLEAN NOT NULL DEFAULT TRUE,   -- Whitelist flag for platform trading
    valid_from DATE NOT NULL,                  -- Introduction / circulation start
    valid_to DATE NULL,                        -- Deprecation date (null if active)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

### Key Benefits of Target Architecture:
1. **SQL-Level Precision**: Database views and financial analytics can compute `amount_in_pence / 10^exponent` directly without application code intervention.
2. **Temporal Validity**: Historic invoices in phased-out currencies (e.g. `HRK` pre-2023) remain valid for audits, while new payments in obsolete currencies are blocked at the database constraint level.
3. **Typo Prevention**: Foreign keys prevent corrupt currency entries (e.g. `'GPD'` instead of `'GBP'`).

## Remediation Plan
1. Create a `currency-service` or include `currency_definitions` in the core configuration schema.
2. Expose currency definitions via gRPC or broadcast cache events via Kafka to all services.
3. Add CDC (Debezium) or cached lookup table in local service databases if FK enforcement is desired.
