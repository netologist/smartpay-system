# SmartPay Platform Developer Story Cards

This directory contains detailed, production-ready developer story cards for building the SmartPay Logistics Payment Platform step-by-step.

---

## 🗺️ Story Map & Dependency Graph

```mermaid
    STORY_001[STORY-001: Ledger Double-Entry Engine<br/>smartpay-ledger-service<br/><b>✅ COMPLETED</b>] --> STORY_003[STORY-003: Payment & Outbox Engine<br/>smartpay-payment-service<br/><b>✅ COMPLETED</b>]
    STORY_001 --> STORY_005[STORY-005: Bank Reconciliation<br/>smartpay-recon-service<br/><b>✅ COMPLETED</b>]
    
    STORY_002[STORY-002: Invoice & ePOD Pricing Engine<br/>smartpay-invoice-service<br/><b>✅ COMPLETED</b>] --> STORY_004[STORY-004: Factoring Payout Worker<br/>smartpay-payout-worker<br/><b>✅ COMPLETED</b>]
    STORY_003 --> STORY_004
    STORY_003 --> STORY_006[STORY-006: Distributed Idempotency Gateway<br/>smartpay-gateway<br/><b>✅ COMPLETED</b>]
    STORY_007[STORY-007: Risk & Fraud Engine<br/>smartpay-risk-service<br/><b>⏳ READY TO PLAY</b>] --> STORY_004
    STORY_003 --> STORY_008[STORY-008: Notification Engine<br/>smartpay-notification-service<br/><b>⏳ READY TO PLAY</b>]
    classDef completed fill:#2e7d32,stroke:#1b5e20,color:#fff,stroke-width:2px;
    classDef ready fill:#1565c0,stroke:#0d47a1,color:#fff,stroke-width:2px;
    classDef blocked fill:#616161,stroke:#424242,color:#fff,stroke-width:2px;

    class STORY_001,STORY_002,STORY_003,STORY_004,STORY_005,STORY_006 completed;
    class STORY_007,STORY_008 ready;
```

---

## 📚 Story Index

| No | Title | Module | Priority | Status | Summary |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **STORY-001** | [Double-Entry Ledger & Atomic Transfer Engine](STORY-001-ledger-double-entry-engine.md) | `smartpay-ledger-service` | P0 | ✅ **Completed** | Zero-sum journal posting, pessimistic balance locking, hold/release lifecycle, Ledger gRPC API on Virtual Threads. |
| **STORY-002** | [Freight Invoicing & ePOD Pricing Engine](STORY-002-invoice-epod-pricing-engine.md) | `smartpay-invoice-service` | P1 | ✅ **Completed** | Delivery proof (ePOD) cryptographic verification, automated freight pricing (base + fuel + VAT), multi-currency invoices. |
| **STORY-003** | [Payment Initiation & Transactional Outbox](STORY-003-payment-initiation-outbox.md) | `smartpay-payment-service` | P1 | ✅ **Completed** | Two-tier idempotency, Ledger gRPC hold reservation, `SKIP LOCKED` transactional outbox event persistence. |
| **STORY-004** | [Carrier Factoring & Instant Payout Worker](STORY-004-payout-factoring-worker.md) | `smartpay-payout-worker` | P1 | ✅ **Completed** | Event-driven Virtual Thread worker consuming ePOD events, applying 2.5% factoring fee, and executing instant payouts via Payment gRPC. |
| **STORY-005** | [Bank Statement & Auto-Reconciliation Engine](STORY-005-bank-reconciliation-engine.md) | `smartpay-recon-service` | P2 | ✅ **Completed** | XXE-hardened CAMT.053 ingestion, four-invariant auto-matching via `end_to_end_id` against ledger gRPC, MATCHED/DISCREPANCY transitions, line query API. |
| **STORY-006** | [API Gateway & Distributed Idempotency Filter](STORY-006-api-gateway-idempotency.md) | `smartpay-gateway` | P2 | ✅ **Completed** | Edge RS256 JWT + tenant isolation, per-IP token bucket rate limiting, servlet two-tier SHA-256 idempotency filter (PROCESSING/COMPLETED/FAILED + TTL reclaim), reverse-proxy route table with response caching and RFC 7807 problem details. |
| **STORY-007** | [Carrier Credit Risk & Fraud Evaluation Engine](STORY-007-carrier-risk-fraud-engine.md) | `smartpay-risk-service` | P1 | ⏳ **Ready to Play** | Carrier credit scoring, exposure limit checks, multi-factor fraud detection gRPC API. |
| **STORY-008** | [Event-Driven Multi-Channel Notification Engine](STORY-008-event-driven-notifications.md) | `smartpay-notification-service` | P2 | ⏳ **Ready to Play** | Consumer group processing of payment/invoice events, templated SMS/Email dispatch, idempotency, DLQ. |
| **TECH-001** | [Containerization & K8s Kustomize Engine](TECH-001-containerization-kustomize-manifests.md) | `k8s/`, `docker/` | P0 | ⏳ **Ready to Play** | Distroless Java 25 multi-stage Dockerfiles, Kustomize base & overlays (dev/staging/prod). |
| **TECH-002** | [Enterprise CI Pipeline Automation](TECH-002-ci-pipeline-automation.md) | `.github/workflows/` | P0 | ⏳ **Ready to Play** | PR matrix validation, unit/integration partitioning, Trivy CVE scanning, GHCR publishing. |
| **TECH-003** | [KinD Ephemeral Cluster & E2E Testing](TECH-003-kind-e2e-testing-pipeline.md) | `.github/workflows/`, `scripts/ci/` | P1 | ⏳ **Ready to Play** | Multi-node KinD cluster, PostgreSQL & Redpanda bootstrap, Kustomize deploy, automated E2E tests. |

---

## 🚦 Recommended Implementation Sequence & Execution Order

Following Domain-Driven Design (DDD) bounded contexts and runtime dependency constraints, stories must be developed in the following phased sequence:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Accounting Core Foundation                                   │
│ [STORY-001] Ledger Double-Entry Engine (smartpay-ledger-service)        │
│ Status: ✅ COMPLETED                                                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
         ┌──────────────────────────┴──────────────────────────┐
         ▼                                                     ▼
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│ Phase 2 (Track A): Commercial Core   │     │ Phase 2 (Track B): Payment Execution │
│ [STORY-002] Freight Invoicing & ePOD │     │ [STORY-003] Payment & Outbox Engine  │
│ Module: smartpay-invoice-service     │     │ Module: smartpay-payment-service     │
│ Status: ✅ COMPLETED                                                   │
└──────────────────┬───────────────────┘     └──────────────────┬───────────────────┘
                   │                                            │
                   └─────────────────────┬──────────────────────┘
                                         ▼
                   ┌───────────────────────────────────────────┐
                   │ Phase 3: Autonomous Settlement & Factoring│
                   │ [STORY-004] Carrier Factoring & Payout    │
                   │ Module: smartpay-payout-worker            │
│ Status: ✅ COMPLETED (S-002 & S-003) │
                   └───────────────────────────────────────────┘
                                         │
                   ┌─────────────────────┴─────────────────────┐
                   ▼                                           ▼
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│ Phase 4 (Audit): Bank Reconciliation │     │ Phase 4 (Ingress): Gateway & Security│
│ [STORY-005] Bank Statement Recon     │     │ [STORY-006] Gateway Idempotency      │
│ Module: smartpay-recon-service       │     │ Module: smartpay-gateway             │
│ Status: ✅ COMPLETED (Reconciles S-001)│     │ Status: ✅ COMPLETED (Cache replay)   │
└──────────────────────────────────────┘     └──────────────────────────────────────┘
```

### Stage 1: Core Financial Ledger Engine (Completed)
* **`STORY-001` (`smartpay-ledger-service`)** — **STATUS: ✅ COMPLETED**
  * **Why First?** The Luca Pacioli double-entry ledger is the source of mathematical truth for funds across the entire system. Without accounts, balances, and gRPC endpoints (`HoldFunds`, `ReleaseHold`, `TransferFunds`), downstream payment and payout services cannot function.

### Stage 2: Invoicing & Payment Execution (Actionable Now)
With `STORY-001` completed, **STORY-002** and **STORY-003** are both unblocked. They can be implemented either sequentially or in parallel by distinct developer streams:

1. **Track A (Recommended Next): `STORY-002` (`smartpay-invoice-service`)**
   * **Dependencies**: None on other microservices (uses `smartpay-common` and per-service Flyway `V1`, schema `invoice`).
   * **Why Implement Next?** Logistics payments originate from deliveries. Freight invoices and cryptographic electronic Proof of Delivery (ePOD) records provide the actual commercial obligations that `STORY-003` settles and `STORY-004` factors.
2. **Track B (Parallel Alternative): `STORY-003` (`smartpay-payment-service`)**
   * **Dependencies**: `smartpay-proto` gRPC stub calling `smartpay-ledger-service` (`STORY-001`), per-service Flyway `V1-V3`, schema `payment`.
   * **Readiness**: Fully unblocked because `LedgerGrpcService` is active and tested. Handles payment intent creation, balance reservation holds, and the Transactional Outbox pattern.

### Stage 3: Automated Factoring & Payouts (Requires Stage 2)
* **`STORY-004` (`smartpay-payout-worker`)** — **STATUS: ✅ COMPLETED**
  * **Prerequisites**: Required **both** `STORY-002` and `STORY-003` (both completed).
  * **Accomplished**: Event-driven worker consuming delivery verification events (`EpodVerifiedEvent`) via Kafka consumer group `smartpay-factoring-workers`, applying 2.5% factoring advance fee, evaluating carrier risk via gRPC, and executing instant Faster Payments disbursements via Payment gRPC on Java 25 Virtual Threads.
### Stage 4: Enterprise Ingress & Bank Audit (Closing Phases)
* **`STORY-005` (`smartpay-recon-service`)** — **STATUS: ✅ COMPLETED**
  * **Prerequisites**: `STORY-001` (Ledger journal entries).
  * **Accomplished**: XXE-hardened CAMT.053 XML ingestion via `POST /api/v1/recon/statements/upload`, automated matching of statement lines to ledger transactions over gRPC `GetTransactionByReference` with four invariant checks (existence, exact-penny amount, currency, entry direction), `MATCHED`/`DISCREPANCY` status transitions, and a statement-line query endpoint. 17 tests (12 unit + 5 integration) passing.
* **`STORY-006` (`smartpay-gateway`)** — **STATUS: ✅ COMPLETED**
  * **Prerequisites**: Downstream REST services (`STORY-002`, `STORY-003`).
  * **Accomplished**: Edge perimeter for `smartpay-payment-service`/`smartpay-invoice-service` with an ordered servlet filter chain — strict security headers + CORS allow-list, token-bucket rate limiting (100 req/min/IP), dependency-free RS256 JWT verification with `tenant_id` claim injection as `X-Tenant-Id` (caller `Authorization` is never forwarded), and a two-tier SHA-256 idempotency filter over `idempotency_records` (gateway schema, Flyway V1) that rejects missing keys (400), detects in-flight conflicts (409), replays completed responses with `X-Cache: IDEMPOTENT-HIT` bypassing the downstream, rejects altered payloads (422), and releases FAILED/expired slots for retry. Reverse-proxy routing passes through non-mutating traffic and emits RFC 7807 problem details. 39 tests (21 unit + 18 integration) passing.

---

## 🔗 Detailed Inter-Service Dependency Matrix

| Story | Service Module | Upstream Dependencies | Integration Method | Downstream Dependents | Playable Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **STORY-001** | `smartpay-ledger-service` | `smartpay-common`, `smartpay-proto` | None (Core Provider) | `STORY-003`, `STORY-004`, `STORY-005` | ✅ **Completed** |
| **STORY-002** | `smartpay-invoice-service` | `smartpay-common` | REST / Domain Events | `STORY-004` | ✅ **Completed** |
| **STORY-004** | `smartpay-payout-worker` | `STORY-002` (Invoices), `STORY-003` (Payments) | Kafka Events & REST/gRPC | None (Terminal consumer) | ✅ **Completed** |
| **STORY-005** | `smartpay-recon-service` | `smartpay-common`, `STORY-001` (Journals) | gRPC `GetTransactionByReference` | External Auditor Reports | ✅ **Completed** |
| **STORY-006** | `smartpay-gateway` | `STORY-002`, `STORY-003` (Downstream routes) | HTTP Reverse Proxy | External Web & Mobile Clients | ✅ **Completed** |

---

## 🛠️ Core Engineering Guidelines
1. **Always Use `smartpay-common`**:
   * Monetary amounts must use `Money`. Never use floating-point types (`double`/`float`).
   * Identifiers must use `AccountId`, `InvoiceId`, `TransactionId`, etc.
   * Domain errors must extend `SmartpayDomainException`.
2. **Synchronous Inter-Service Calls**:
   * Use gRPC client stubs generated from `smartpay-proto`. Refer to the [gRPC Technical Guide](../architecture/grpc-technical-guide.md).
3. **Virtual Threads Safety**:
   * Avoid `synchronized` methods to prevent carrier thread pinning (favor `ReentrantLock` or immutable records).
   * Utilize `Executors.newVirtualThreadPerTaskExecutor()` for concurrent task pools.
4. **Transactional Outbox & Event Streaming**:
   * Never execute dual-writes (DB update + Kafka produce). State mutations and outbox records must be committed in the same database transaction, polled via `SELECT ... FOR UPDATE SKIP LOCKED`.

---

## 🧪 Test Execution Commands (Unit vs Integration)

To run tests efficiently during development, use the partitioned Maven profiles:

* **Unit Tests (`mvn test -Punit`)**:
  Runs fast, in-memory domain and business logic tests without launching Docker containers (~6s):
  ```bash
  mvn test -Punit
  # Target specific module:
  mvn test -Punit -pl smartpay-invoice-service
  ```

* **Integration Tests (`mvn test -Pintegration`)**:
  Runs full Testcontainers PostgreSQL 16, HTTP/2 gRPC, and MockMvc slice integration tests (~30s):
  ```bash
  mvn test -Pintegration
  # Target specific module:
  mvn test -Pintegration -pl smartpay-ledger-service
  ```

* **All Tests Default (`mvn test`)**:
  Runs all 150 automated tests across modules:
  ```bash
  mvn test -pl smartpay-common,smartpay-ledger-service,smartpay-invoice-service
  ```
