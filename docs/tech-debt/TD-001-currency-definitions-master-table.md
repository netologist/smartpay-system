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

Currently, currencies are checked via Java's standard `java.util.Currency` and `com.hozgan.smartpay.common.model.Money`, with database tables having `VARCHAR(3)` fields without a relational foreign key to a centralized `currency_definitions` master table.

## Why Deferred at the Database Layer?
1. **Microservice Database Decoupling**: Each service (`ledger`, `invoice`, `recon`) owns its independent PostgreSQL database. Enforcing foreign keys across databases would either require Postgres Foreign Data Wrappers (FDW) or duplicating and synchronizing the reference table in every service database.
2. **Current Scope**: Early phase operations primarily settle in `GBP`, with cross-border support for `EUR` and `USD`. All currently supported currencies have an exponent of 2 (100 minor units = 1 major unit), which `Money` handles uniformly.

---

## Proposed Implementation Options

### Option A: `smartpay-common` Domain Registry (Recommended Intermediate Step)
Before introducing a dedicated microservice or duplicating SQL tables across all databases, the cleanest and highest-performance approach is an in-memory, thread-safe Domain Registry within `smartpay-common`. This provides:
- $O(1)$ CPU-speed lookup with zero network/database latency.
- Full temporal validity checks (`valid_from`, `valid_to`).
- Exponent lookup for 0, 2, and 3-decimal currencies.
- Strict platform whitelisting (`isActive`).

#### Proposed Class Implementations:

```java
package com.hozgan.smartpay.common.model.currency;

import java.time.LocalDate;
import java.util.Objects;

/**
 * ISO-4217 Currency Definition value object.
 *
 * @param code         3-letter ISO-4217 currency code (e.g. "GBP", "EUR")
 * @param numericCode  3-digit ISO-4217 numeric code (e.g. "826", "978")
 * @param name         Full currency name (e.g. "Pound Sterling")
 * @param symbol       Display symbol (e.g. "£", "€", "$")
 * @param exponent     Number of decimal fraction digits / minor unit power of 10 (e.g. 2 for GBP, 0 for JPY, 3 for BHD)
 * @param isActive     Whether the currency is actively enabled for transactions on SmartPay
 * @param validFrom    Date the currency was introduced into legal circulation
 * @param validTo      Date the currency was decommissioned (null if currently circulating)
 */
public record CurrencyDefinition(
        String code,
        String numericCode,
        String name,
        String symbol,
        int exponent,
        boolean isActive,
        LocalDate validFrom,
        LocalDate validTo
) {

    public CurrencyDefinition {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(numericCode, "numericCode cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(validFrom, "validFrom cannot be null");

        if (code.length() != 3) {
            throw new IllegalArgumentException("Currency code must be 3 uppercase letters: " + code);
        }
        if (exponent < 0 || exponent > 4) {
            throw new IllegalArgumentException("Currency exponent must be between 0 and 4, got: " + exponent);
        }
    }

    /**
     * Checks if this currency was in legal circulation on the specified date.
     */
    public boolean isValidOn(LocalDate date) {
        Objects.requireNonNull(date, "date cannot be null");
        boolean afterOrOnStart = !date.isBefore(validFrom);
        boolean beforeOrOnEnd = validTo == null || !date.isAfter(validTo);
        return afterOrOnStart && beforeOrOnEnd;
    }

    /**
     * Checks if this currency is currently valid and active on the platform today.
     */
    public boolean isCurrentlyActive() {
        return isActive && isValidOn(LocalDate.now());
    }
}
```

```java
package com.hozgan.smartpay.common.model.currency;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * High-performance, thread-safe in-memory registry of supported ISO-4217 currencies.
 */
public final class Currencies {

    public static final CurrencyDefinition GBP = new CurrencyDefinition(
            "GBP", "826", "Pound Sterling", "£", 2, true,
            LocalDate.of(1971, 2, 15), null
    );

    public static final CurrencyDefinition EUR = new CurrencyDefinition(
            "EUR", "978", "Euro", "€", 2, true,
            LocalDate.of(1999, 1, 1), null
    );

    public static final CurrencyDefinition USD = new CurrencyDefinition(
            "USD", "840", "US Dollar", "$", 2, true,
            LocalDate.of(1792, 4, 2), null
    );

    // Zero-decimal currency example
    public static final CurrencyDefinition JPY = new CurrencyDefinition(
            "JPY", "392", "Japanese Yen", "¥", 0, false, // Disabled initially
            LocalDate.of(1871, 6, 27), null
    );

    // 3-decimal currency example
    public static final CurrencyDefinition BHD = new CurrencyDefinition(
            "BHD", "048", "Bahraini Dinar", ".د.ب", 3, false,
            LocalDate.of(1965, 10, 7), null
    );

    // Decommissioned historic currency example (Croatian Kuna phased out on 2023-01-01)
    public static final CurrencyDefinition HRK = new CurrencyDefinition(
            "HRK", "191", "Croatian Kuna", "kn", 2, false,
            LocalDate.of(1994, 5, 30), LocalDate.of(2022, 12, 31)
    );

    private static final Map<String, CurrencyDefinition> REGISTRY;

    static {
        Map<String, CurrencyDefinition> map = new HashMap<>();
        map.put(GBP.code(), GBP);
        map.put(EUR.code(), EUR);
        map.put(USD.code(), USD);
        map.put(JPY.code(), JPY);
        map.put(BHD.code(), BHD);
        map.put(HRK.code(), HRK);
        REGISTRY = Collections.unmodifiableMap(map);
    }

    private Currencies() {
        // static utility
    }

    public static Optional<CurrencyDefinition> find(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(REGISTRY.get(code.toUpperCase()));
    }

    public static CurrencyDefinition get(String code) {
        return find(code).orElseThrow(() ->
                new IllegalArgumentException("Unsupported currency code: " + code));
    }

    public static int getExponent(String code) {
        return get(code).exponent();
    }

    public static boolean isSupported(String code) {
        return find(code).map(CurrencyDefinition::isCurrentlyActive).orElse(false);
    }

    public static void requireActive(String code) {
        CurrencyDefinition def = get(code);
        if (!def.isCurrentlyActive()) {
            throw new IllegalStateException(
                    String.format("Currency %s is not active for transactions on this platform", code));
        }
    }
}
```

---

### Option B: Database Master Table (Target Enterprise Architecture)
When the system introduces a dedicated Configuration / Master Data Management (MDM) microservice, create the relational table in PostgreSQL:

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

-- Seed Data
INSERT INTO currency_definitions (currency_code, numeric_code, name, symbol, exponent, is_active, valid_from, valid_to)
VALUES 
    ('GBP', '826', 'Pound Sterling', '£', 2, TRUE, '1971-02-15', NULL),
    ('EUR', '978', 'Euro', '€', 2, TRUE, '1999-01-01', NULL),
    ('USD', '840', 'US Dollar', '$', 2, TRUE, '1792-04-02', NULL),
    ('JPY', '392', 'Japanese Yen', '¥', 0, FALSE, '1871-06-27', NULL),
    ('BHD', '048', 'Bahraini Dinar', '.د.ب', 3, FALSE, '1965-10-07', NULL),
    ('HRK', '191', 'Croatian Kuna', 'kn', 2, FALSE, '1994-05-30', '2022-12-31');
```

### Key Benefits of Target Architecture:
1. **SQL-Level Precision**: Database views and financial analytics can compute `amount_in_pence / 10^exponent` directly without application code intervention.
2. **Temporal Validity**: Historic invoices in phased-out currencies (e.g. `HRK` pre-2023) remain valid for audits, while new payments in obsolete currencies are blocked at the database constraint level.
3. **Typo Prevention**: Foreign keys prevent corrupt currency entries (e.g. `'GPD'` instead of `'GBP'`).

---

## Remediation & Migration Plan
1. **Phase 1 (Current)**: Enforce currency columns as `VARCHAR(3) NOT NULL DEFAULT 'GBP'` in `invoices`, `accounts`, `journal_entries`, and `bank_statements`.
2. **Phase 2 (Near-Term)**: Implement `CurrencyDefinition` and `Currencies` registry in `smartpay-common` for in-memory temporal checks and non-2 exponent support.
3. **Phase 3 (Long-Term)**: Deploy central `currency_definitions` table in MDM service; synchronize via Kafka topic `system.currency.definitions` or Debezium CDC to edge service databases.
