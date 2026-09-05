# STORY-001: Double-Entry Ledger & Atomic Balance Transfer Engine

## 📌 Overview
* **Target Module**: `smartpay-ledger-service`
* **Priority**: P0 (Core Accounting Engine)
* **Associated Database Tables**: `accounts`, `account_balances`, `journal_transactions`, `journal_entries` (`V1`, `V2`)
* **Associated gRPC Contract**: `smartpay-proto/src/main/proto/ledger.proto`
* **Required `smartpay-common` Components**:
  * `Money` (Financial arithmetic and scale-normalized currency operations)
  * `AccountId`, `TransactionId`, `IdempotencyKey` (Strongly-typed IDs)
  * `EntryType` (`DEBIT`, `CREDIT`), `JournalStatus` (`POSTED`, `REVERSED`)
  * `InsufficientFundsException`, `UnbalancedJournalTransactionException`, `CurrencyMismatchException`, `AccountNotFoundException`
  * `LedgerTransactionPostedEvent`

---

## 🎯 User Story
> **As the** Core Financial Platform,  
> **I want to** execute atomic, double-entry, zero-sum journal transactions across platform accounts,  
> **So that** funds provenance is mathematically proven, balance leaks and negative balances are prevented under high concurrency, and a complete financial audit trail is maintained.

---

## 📐 Architecture & Domain Invariants

1. **Zero-Sum Journal Invariant**:
   Every transaction header (`journal_transactions`) must consist of entry lines (`journal_entries`) where total debits equal total credits:
   $$\sum \text{Debit} = \sum \text{Credit}$$
   If debits and credits do not match, the entire operation must abort immediately by throwing `UnbalancedJournalTransactionException`.

2. **Immutability (Append-Only Ledger)**:
   Ledger lines cannot be modified or deleted. The database trigger `trg_prevent_ledger_modification` rejects any `UPDATE` or `DELETE` attempt at the database engine level.

3. **Pessimistic Locking & Overdraft Protection**:
   Balance updates must acquire a `PESSIMISTIC_WRITE` lock:
   * To eliminate deadlocks when transferring funds between two accounts, locks must always be acquired in strictly ascending order of their `AccountId`:
     `id1.compareTo(id2) < 0 ? lock(id1), lock(id2) : lock(id2), lock(id1)`
   * Available balance: `available = clearedBalance - holdBalance`. If available balance is less than the requested amount, abort with `InsufficientFundsException`.

4. **Idempotency**:
   Subsequent requests bearing an identical `idempotency_key` must return the existing `JournalTransactionEntity` without modifying balances.

---

## ✅ Acceptance Criteria (AC)

### AC-1: Zero-Sum Journal Validation
* **Given**: A journal transaction request containing £200 DEBIT and £200 CREDIT lines,
* **When**: The transaction is committed,
* **Then**: A `journal_transactions` row (`status = POSTED`, `UUIDv7`) and 2 `journal_entries` rows must be persisted, returning the `TransactionId`.
* **And**: If debits and credits differ (e.g., £200 DEBIT vs £190 CREDIT), `UnbalancedJournalTransactionException` must be thrown and no data written.

### AC-2: Atomic Funds Transfer (TransferFunds)
* **Given**: Account A with £500 available balance, Account B with £100 available balance,
* **When**: A transfer of £200 from A to B is executed,
* **Then**: Account A balance decrements to £300, Account B balance increments to £300, and a double-entry journal posting (DEBIT A £200, CREDIT B £200) is created atomically.
* **And**: If Account A has insufficient available balance, no balances are updated and `InsufficientFundsException` is thrown.

### AC-3: Balance Hold Reservation & Release/Capture
* **Given**: An account with £1000 cleared balance and £0 hold balance,
* **When**: `holdFunds(accountId, £300, refId)` is invoked,
* **Then**: `hold_balance_pence` increases by £300, reducing available balance to £700 (`cleared_balance` remains £1000).
* **When**: `releaseHold(..., capture = true)` is invoked:
  * `hold_balance_pence` decreases by £300 and `cleared_balance_pence` decreases by £300 (funds permanently captured).
* **When**: `releaseHold(..., capture = false)` is invoked:
  * `hold_balance_pence` decreases by £300 while `cleared_balance` remains unchanged; available balance returns to £1000 (hold canceled).

### AC-4: gRPC Endpoint Implementation
* **Given**: The `smartpay-proto` definition for `LedgerServiceGrpc.LedgerServiceImplBase`,
* **When**: External services invoke `GetBalance`, `TransferFunds`, or `HoldFunds`,
* **Then**: The gRPC controller delegates to application services and returns responses serialized as `MoneyProto` and `UUIDProto`.

---

## 💻 Class Implementation Structure

```
smartpay-ledger-service/src/main/java/com/hozgan/smartpay/ledger/
├── service/
│   ├── AccountBalanceService.java      // Balance queries, pessimistic locking, hold/release
│   ├── LedgerDomainService.java        // Double-entry validation and append-only persistence
│   └── impl/
│       ├── AccountBalanceServiceImpl.java
│       └── LedgerDomainServiceImpl.java
├── grpc/
│   ├── LedgerGrpcService.java          // Extends LedgerServiceGrpc.LedgerServiceImplBase
│   └── mapper/
│       └── LedgerProtoMapper.java      // Money <-> MoneyProto, AccountBalance <-> GetBalanceResponse
└── config/
    └── GrpcServerConfig.java           // Netty server port and lifecycle configuration
```

### Application Service Signatures:
```java
public interface AccountBalanceService {
    AccountBalance getBalance(AccountId accountId);
    AccountBalance holdFunds(AccountId accountId, Money amount, String referenceId, IdempotencyKey key);
    AccountBalance releaseHold(AccountId accountId, String holdId, Money amount, boolean capture);
    void transfer(AccountId source, AccountId target, Money amount, String reference, IdempotencyKey key);
}
```
