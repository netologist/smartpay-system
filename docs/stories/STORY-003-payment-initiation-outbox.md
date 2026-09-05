# STORY-003: Payment Initiation & Transactional Outbox Engine

## 📌 Overview
* **Target Module**: `smartpay-payment-service`
* **Priority**: P1 (Disbursement & Distributed Consistency)
* **Associated Database Tables**: `transactional_outbox` (`V4`), `idempotency_records` (`V5`)
* **Associated gRPC Dependency**: `smartpay-ledger-service` (`smartpay-proto/src/main/proto/ledger.proto`)
* **Required `smartpay-common` Components**:
  * `Money` (Payment and hold amounts)
  * `AccountId`, `TenantId`, `IdempotencyKey`, `EndToEndId` (Strongly-typed IDs)
  * `IdempotencyStatus` (`PROCESSING`, `COMPLETED`, `FAILED`)
  * `IdempotencyConflictException`, `RequestHashMismatchException`, `DuplicateTransactionException`
  * `OutboxEvent` (At-least-once outbox persistence)

---

## 🎯 User Story
> **As a** Payment Orchestration Service,  
> **I want to** initiate Faster Payments and Open Banking disbursements with two-tier idempotency locking, reserve balances via Ledger gRPC, and commit events to a Transactional Outbox,  
> **So that** duplicate withdrawals are impossible during network retries and events are guaranteed to reach Redpanda/Kafka without dual-write data loss.

---

## 📐 Architecture & Distributed Consistency Rules

1. **Transactional Outbox Pattern (Dual-Write Prevention)**:
   * Both business entities and the `transactional_outbox` row (`JSONB` payload) must be committed within the identical database transaction.
   * Direct message publishing to Kafka during the web request is prohibited; an asynchronous worker polls the outbox table.

2. **Two-Tier Distributed Idempotency**:
   * **Step 1**: Compute the SHA-256 digest of the request payload (`request_hash`).
   * **Step 2**: Insert into `idempotency_records` with status `PROCESSING`. If a row exists with status `PROCESSING`, throw `IdempotencyConflictException`. If the existing hash differs, throw `RequestHashMismatchException`.
   * **Step 3**: Upon completion, transition status to `COMPLETED` and cache the response. Duplicate requests immediately return the cached payload with header `X-Cache: IDEMPOTENT-HIT`.

3. **Ledger gRPC Synchronization**:
   * The payment service must invoke `HoldFunds` on `smartpay-ledger-service` via gRPC before triggering external bank APIs.

---

## ✅ Acceptance Criteria (AC)

### AC-1: Idempotent Payment Initiation
* **Given**: A valid payment request for £500,
* **When**: `initiatePayment(request)` is executed,
* **Then**: An `idempotency_records` row is created, `HoldFunds` is called on Ledger gRPC, and a `transactional_outbox` row (`PAYMENT_INITIATED`) is committed atomically.

### AC-2: Duplicate Request Protection
* **Given**: A previously completed payment request,
* **When**: A second request arrives with the identical `idempotency_key` and matching body hash,
* **Then**: Ledger is not re-invoked, no new outbox event is stored, and the cached response is returned immediately.

### AC-3: Outbox Polling Invariant (SKIP LOCKED)
* **Given**: Unprocessed rows (`processed_at IS NULL`) in `transactional_outbox`,
* **When**: The outbox worker executes,
* **Then**: Rows are fetched using `ORDER BY created_at ASC FOR UPDATE SKIP LOCKED`, dispatched to Kafka, and updated with `processed_at = NOW()`.

---

## 💻 Class Implementation Structure

```
smartpay-payment-service/src/main/java/com/hozgan/smartpay/payment/
├── service/
│   ├── PaymentService.java             // Initiation, settlement, cancellation
│   ├── IdempotencyService.java         // Two-tier SHA-256 lock state machine
│   └── impl/
│       ├── PaymentServiceImpl.java
│       └── IdempotencyServiceImpl.java
├── grpc/
│   └── client/
│       └── LedgerGrpcClient.java       // Stubs and channel wrapper for Ledger RPCs
├── worker/
│   └── OutboxEventPublisherWorker.java // @Scheduled Virtual Thread outbox publisher
└── web/
    └── PaymentController.java          // REST POST /api/v1/payments/initiate
```
