# Technical Debt Record: TD-004

## Title
**Cross-Service Distributed Transactions: No Saga Pattern — Multi-Step Flows Lack Durable State, Compensation, and Timeouts**

## Status
**Recorded / Deferred** (Priority: High — money can be stranded on partial failure)
**Decision: open.** Sections below document the full option space (A–D) with implementation detail so the solution can be decided later. Recommendation when scheduled: **Option C (orchestrated saga)**. Option-independent groundwork (see below) can and should start before the architectural decision is locked.

## Date
2026-09-08

---

## 📌 Problem Statement & Context

The **Saga pattern is not applied anywhere** in the SmartPay codebase — a repository-wide search for `saga`, orchestrator, or compensation logic returns zero implementation hits (only docs/ADR prose). Long-running business transactions that span multiple services are executed as **best-effort sequential calls**: no saga state machine, no correlation record, no timeout sweeper, and no compensating actions. Local ACID transactions and the transactional outbox (ADR-005) cover *single-service* consistency only; they cannot roll back a side effect already committed in *another* service.

Two cross-service flows are affected today or imminently:

### 1. Payment initiation — orphaned ledger holds (live)
`PaymentService.initiatePayment` (`smartpay-payment-service`) runs:
1. Idempotency lock (`REQUIRES_NEW` TX) → 2. gRPC `LedgerGrpcClient.holdFunds` (reserves `hold_balance_pence` on the ledger) → 3. `commitPaymentAtomically` (outbox `PAYMENT_INITIATED` + idempotency `COMPLETED` in one local TX).

If step 3 fails after step 2 succeeded (DB outage, serialization failure, process crash between the two calls), the outbox insert rolls back **but the ledger hold does not** — funds remain reserved indefinitely. The compensating RPC `releaseHold(..., capture = false)` (`HOLD_CANCEL`) already exists in `LedgerGrpcService`/`LedgerGrpcClient` and is integration-tested, yet it has **zero production callers** (only tests invoke it). The ledger also has **no scheduled hold-expiry/cleanup sweeper** (no `@Scheduled` outside the payment outbox poller), so an orphaned hold has no automatic resolution path.

### 2. Factoring advance — partial payout state (live)
`FactoringPayoutWorker.processDeliveryVerification` (`smartpay-payout-worker`, Kafka-triggered) sequentially performs:
1. Invoice fetch + eligibility/anti-collusion/fee/cap checks (read-only) → 2. Risk gRPC (read-only) → 3. **Payment gRPC `initiateDisbursement`** (side effect: ledger hold + outbox `PAYMENT_INITIATED`; idempotency key `FACTORING-ADVANCE-INV-<invoiceId>`) → 4. **`invoiceClient.updateInvoiceStatus(FACTORING_APPROVED)`** (side effect) → 5. **`FactoringEventPublisher.publishApprovedPayout`** (side effect: direct `kafkaTemplate.send(...).get(5s)` to `smartpay.events.factoring`).

Failure windows with no compensation, resume, or durable trace:
* Steps 3 succeeds → step 4 fails (network): money is reserved as an advance but the invoice stays `EPOD_VERIFIED`. A Kafka re-delivery re-enters the worker; the payment step returns the cached `INITIATED` idempotency response, so the flow never progresses or auto-compensates — operator intervention required to reconcile a "half-approved" factoring advance.
* Steps 3 and 4 succeed → step 5 fails: the advance is fully executed but no `FactoringPayoutApprovedEvent` reaches notification/audit consumers. Publishing happens **after** the side effects via a direct Kafka send (the stateless worker has no outbox), so the event is simply lost.
* The whole multi-step monetary business transaction has **no saga instance record** — nothing persists which steps completed, which compensation ran, or when the transaction started/deadlined. Stuck vs. in-flight vs. failed cannot be distinguished operationally or by audit.

### 3. Payment settlement tail — unbuilt, needs saga groundwork first
`OutboxEventPublisherWorker.processEvent` (`smartpay-payment-service`) currently only marks outbox rows `processed_at`; bank-rail dispatch, `ReleaseHold(capture = true)`, and `PaymentSettledEvent` are not yet implemented (javadoc defers them; payment status is always `INITIATED`). When that tail is built it will be a third multi-service saga (hold → bank → ledger capture → event) and must be designed with compensations from day one rather than retrofitted.

### Adjacent event-pipeline gaps surfaced during this analysis (prerequisites, tracked here for context)
* `smartpay-invoice-service` emits `EpodVerifiedEvent` only to the in-process Spring `ApplicationEventPublisher` (`EpodService`); no `KafkaTemplate` producer or outbox bridge exists in the module — whole-repo `kafkaTemplate.send` matches are only `FactoringEventPublisher` (payout) and the notification DLQ publisher. The `smartpay.events.invoice` topic (and therefore the payout worker) is not produced by current code.
* Default topic names disagree: payout worker publishes to `smartpay.events.factoring` while `smartpay-notification-service` listens for payout events on `smartpay.events.payout`.

These must be repaired before any saga commands/outcomes can rely on the event bus; they are candidates for separate TD records.

---

## ⚖️ Option-Independent Groundwork (applies to every option)

The following work is required regardless of which option (A–D) is chosen. It produces safety value on its own and is the recommended first step while the architectural decision stays open:

1. **Compensation primitives**
   * Wire the first real production caller of `LedgerGrpcClient.releaseHold(accountId, holdId, amount, capture = false)` (`HOLD_CANCEL` — exists and is integration-tested; currently zero callers).
   * `smartpay-payment-service`: add `CANCELLED`/`FAILED` payment status handling and a `PAYMENT_CANCELLED` outbox event type — today only `PAYMENT_INITIATED` exists and `getPaymentStatus` pins every payment to `INITIATED`.
   * `smartpay-invoice-service`: add an idempotent status-revert operation (`FACTORING_APPROVED` → `EPOD_VERIFIED`).
2. **Repair the event bus** (facts in the Context section): give `smartpay-invoice-service` a real Kafka producer/outbox bridge for `EpodVerifiedEvent`; align payout topic defaults (`smartpay.events.factoring` vs notification's `smartpay.events.payout`). No saga survives a bus nobody produces to.
3. **Per-step idempotency**: extend the existing per-invoice key pattern (`FACTORING-ADVANCE-INV-<invoiceId>` in `PaymentGrpcClient`) to every new step/operation introduced by the chosen option.
4. **Correlation id**: formalize a saga correlation field on `smartpay-common` event records (invoice/load id already acts as the de-facto key).

---

## 🧭 Detailed Solution Options

### Option A — Harden the Status Quo (best-effort + patches)

**Concept.** Keep the current sequential orchestration; make each step locally retryable and compensated; resolve anything that still gets stuck through scheduled reconciliation and an ops runbook. No new saga state.

**Implementation.**
* `PaymentService.initiatePayment`: after a successful `holdFunds`, guard the atomic commit and compensate on failure:
```java
HoldResult hold = ledgerClient.holdFunds(debtorAccountId, amount, endToEndId, idempotencyKey.value());
try {
    return self.commitPaymentAtomically(tenantId, idempotencyKey, paymentId,
            debtorAccountId, creditorAccountId, amount, endToEndId,
            hold.holdId(), request);
} catch (Exception e) {
    // The outbox/idempotency TX rolled back, but the ledger hold did not.
    ledgerClient.releaseHold(debtorAccountId, hold.holdId(), amount, false); // HOLD_CANCEL
    idempotencyService.markFailed(idempotencyKey, requestHash);               // new op
    throw e;
}
```
* Payment reconciliation job (new `@Scheduled` poller in `smartpay-payment-service`, mirroring `OutboxEventPublisherWorker`): find `COMPLETED` initiations whose hold was never captured or cancelled within a TTL and invoke `releaseHold(capture = false)`. This closes the missing hold-expiry gap without changing ledger semantics (the ledger cannot know intent; the payment side can).
* Payout worker resume semantics: a step failure throws → the Kafka message is **not acknowledged → redelivered** → the per-invoice idempotent payment step returns the cached `INITIATED` response → the failed step (invoice update or event publish) is retried. Add Resilience4j retry/backoff (dependency already present) with max attempts, then DLQ + alert for persistent failures. The stateless worker needs no local state because the broker + idempotency records ARE the resume mechanism.
* Ops runbook for DLQ entries: query payment/invoice state by invoiceId, then execute the manual compensation — `releaseHold(capture = false)` + invoice revert — or re-drive the event.

**Pros.** Smallest diff; fixes the acute orphaned-hold and stuck-advance cases; leverages two-tier idempotency already built; no new tables or components.
**Cons.** Non-transient failures still leave money reserved until an operator acts; no distributed audit record of step progress; no saga deadlines (only the TTL reconciliation job); every future saga (payment settlement tail) re-invents the same bespoke patches.
**Effort.** ~0.5–1 week incl. tests. **Best when:** a fast safety net is needed now and a real saga will be chosen later (this is strictly a stopgap, not an endpoint).

---

### Option B — Choreographed Saga (no central coordinator)

**Concept.** Each participant commits its side effect, records local participation state, and reacts to domain events — including failure and compensation events — using the correlation id (invoiceId) as the join key. Control flow emerges from the event handlers; nobody drives the whole transaction.

**Implementation.**
* Every side-effecting participant keeps a local record of what it did for a correlation id. Payment-service already has this in the outbox row (`PAYMENT_INITIATED`); invoice-service adds a lightweight step/status history for factoring approvals.
* New failure domain events flow back over the existing bus:
  * `FactoringAdvanceFailedEvent(invoiceId, failedStep, reason)` — emitted by the payout worker when a step after payment initiation fails.
  * `PaymentCancelledEvent` — emitted by payment-service when a payment is compensated.
* Wiring example (factoring advance):
  1. Payment initiated (hold placed, outbox `PAYMENT_INITIATED`).
  2. Invoice update step fails → worker publishes `FactoringAdvanceFailedEvent(failedStep = INVOICE_APPROVAL)`.
  3. Payment-service consumes it (same `@KafkaListener` machinery as notification), sees an `INITIATED` payment for that invoiceId, executes `releaseHold(capture = false)` and emits `PAYMENT_CANCELLED`.
  4. Invoice-service, on `PaymentCancelledEvent`, reverts `FACTORING_APPROVED` → `EPOD_VERIFIED` if it had advanced (idempotent guard: no-op when already `EPOD_VERIFIED`).
  5. Publish-step failure follows the same path (worker emits the failure event; compensation is shared).
* Timeouts: per-participant TTL sweepers over their local state (payment-service sweeps initiations awaiting a terminal event; invoice-service sweeps approvals awaiting a terminal event) — or one lightweight read-only "watchdog" that only raises alerts, never drives steps.
* Loop/cascade guards: every handler is idempotent and its state transitions are guarded; each event type is consumed exactly once per consumer group (ADR-008); correlation id is logged at every hop.

**Pros.** No coordinator to build or operate; services stay decoupled; fits the existing outbox + `@KafkaListener` + consumer-group architecture (ADR-005/008); natural when teams own services independently; scales to many flows with no central choke point.
**Cons.** The flow is implicit — no single place answers "where is this factoring advance?"; failure handling is scattered across handlers, so the failure matrix is hard to verify and test; risk of compensation cascades between services; needs disciplined correlation-id logging and event versioning; weakest option for **auditing a monetary flow** across many handlers.
**Effort.** ~1.5–2.5 weeks. **Best when:** failures are rare, flows have 2–3 participants, and service-team autonomy matters more than central auditability.

---

### Option C — Orchestrated Saga (recommended target)

**Concept.** A durable coordinator owns one saga instance per business transaction, issues step commands, awaits outcome events, advances an explicit state machine, and on failure or timeout executes compensations in **reverse order**. State lives in a DB table owned by the orchestrator; rows are claimed with `FOR UPDATE SKIP LOCKED` exactly like the outbox (ADR-005); commands/outcomes flow over Kafka with a saga correlation id and per-step idempotency keys.

**Runtime components.**
1. **State model** — `saga_instances` in the orchestrator schema:
```sql
CREATE TABLE saga_instances (
    id             UUID PRIMARY KEY,              -- UUIDv7 (com.hozgan.smartpay.common.util.UuidV7)
    saga_type      VARCHAR(64)  NOT NULL,         -- e.g. 'FACTORING_ADVANCE'
    correlation_id VARCHAR(64)  NOT NULL,         -- aggregate scope, e.g. invoiceId
    status         VARCHAR(24)  NOT NULL,         -- STARTED|RUNNING|SUCCEEDED|FAILED|COMPENSATING|COMPENSATED
    current_step   INT          NOT NULL DEFAULT 0,
    steps          JSONB        NOT NULL,         -- [{name, state: PENDING|SUCCEEDED|FAILED|COMPENSATED,
                                                  --   compensation, attempts}]
    payload        JSONB        NOT NULL,         -- saga input: ids, amounts, holdId, risk result
    deadline_at    TIMESTAMPTZ  NOT NULL,         -- start + saga timeout
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (saga_type, correlation_id)            -- one live saga per aggregate
);

CREATE INDEX idx_saga_instances_sweeper ON saga_instances (status, deadline_at)
    WHERE status IN ('STARTED','RUNNING');
```
All orchestrator writes (saga row + outbox command row) happen in **one local transaction** (ADR-005). Strongly-typed IDs from `com.hozgan.smartpay.common.model.id.*` are used on the wire; the table stores their scalar form.
2. **Coordinator loop** (new package `com.hozgan.smartpay.payment.saga` in the orchestrator):
```
beginSaga(type, correlationId, payload):
    TX: insert saga_instances (STARTED, deadline_at = now + sagaTimeout)
        + outbox row (command for step 1)

poller @Scheduled (mirrors OutboxEventPublisherWorker):
    TX: claim RUNNING saga rows + their pending command rows
        FOR UPDATE SKIP LOCKED → dispatch to virtual-thread pool

onOutcome(sagaId, stepId, SUCCEEDED | FAILED):
    TX: update steps JSONB
        FAILED             → status = COMPENSATING, run compensate(saga)
        SUCCEEDED, more    → enqueue next step command (outbox row)
        SUCCEEDED, last    → status = SUCCEEDED

sweeper @Scheduled:
    TX: claim RUNNING rows with deadline_at < now() (SKIP LOCKED)
        → status = COMPENSATING, run compensate(saga)

compensate(saga):
    for step in succeeded steps in REVERSE order:
        run step.compensation (idempotent, keyed)
        mark step COMPENSATED
    status = COMPENSATED            // every compensation succeeded
           | COMPENSATION_FAILED    // else: alert + bounded backoff retry
```
Dispatcher uses `Executors.newVirtualThreadPerTaskExecutor()`; **no `synchronized`** (Project Loom); FSM transitions are pure logic over the `steps` JSONB executed inside the local TX.
3. **Step/compensation matrix — `SAGA-FACTORING-ADVANCE`** (steps after the read-only gates):

| # | Forward action (participant) | Failure / timeout compensation | Compensation idempotency |
|---|------------------------------|--------------------------------|--------------------------|
| S1 | Payment initiation: ledger `holdFunds` + outbox `PAYMENT_INITIATED` (`smartpay-payment-service`) | `LedgerGrpcClient.releaseHold(accountId, holdId, amount, capture = false)` → `HOLD_CANCEL` restores available balance (gRPC exists, integration-tested); payment marked `CANCELLED` + outbox `PAYMENT_CANCELLED` | `releaseHold` already takes an idempotency key; status flip guarded by the idempotency record — replay-safe |
| S2 | Invoice state → `FACTORING_APPROVED` (`smartpay-invoice-service`, REST) | New restore operation reverting the invoice to `EPOD_VERIFIED` | Restore keyed by idempotency key; a repeated compensation is a no-op once status is `EPOD_VERIFIED` |
| S3 | Publish `FactoringPayoutApprovedEvent` → `smartpay.events.factoring` (terminal) | None — terminal step; make dispatch **durable/retryable** (outbox) instead of the current direct `kafkaTemplate.send(...).get(5s)` after side effects | Publish keyed by invoiceId; consumer idempotency handles duplicates |

   **`SAGA-PAYMENT-SETTLE`** (future tail): S1 `holdFunds` (precondition, executed at initiation) → S2 bank-rail dispatch (timeout/failure → `releaseHold(capture = false)` + payment `FAILED`) → S3 ledger `ReleaseHold(capture = true)` + zero-sum posting (failure *after* bank settlement is reconciled via `end_to_end_id` matching in `smartpay-recon-service` + reversal journal per ADR-003) → S4 `PaymentSettledEvent` (terminal, at-least-once).
4. **Participant roles.** Payment-service is both coordinator and S1 participant (fine — S1 is invoked locally but still recorded as a step). The payout worker shrinks to the read-only gates (invoice fetch, eligibility, risk score, fee math) and then calls `BeginFactoringAdvance` gRPC/REST carrying the calculation + risk result; it no longer performs side effects directly. Invoice-service consumes an `ApproveInvoice` command and exposes the idempotent revert op.
5. **Idempotency & correlation.** Per-step keys `SAGA-<sagaId>-STEP-<n>`; the payment step keeps `FACTORING-ADVANCE-INV-<invoiceId>` (stable across saga attempts so a redelivered event cannot double-place a hold). Outcome events carry a nullable `sagaId` field on the existing `smartpay-common` records — backward compatible.
6. **HA.** Orchestrator pods are stateless replicas sharing `saga_instances`; `SKIP LOCKED` guarantees single-claim of saga rows and outbox rows, exactly like the outbox worker today.

**Pros.** Single auditable source of truth per distributed transaction — state, progress, compensations, and deadlines are queryable; the failure matrix is explicit in one state machine instead of scattered try/catches; deterministic reverse-order compensation; centralized timeouts; existing primitives plug straight in (ledger `HOLD_CANCEL`, ADR-003 reversal postings); the unbuilt payment-settlement tail becomes a second saga definition on the same runtime.
**Cons.** New coordinator + `saga_instances` table; participants must expose compensating operations (S2 revert is net-new); step latency through the bus; step contracts must be versioned.
**Effort.** ~2–4 weeks, phased (see Remediation Plan). **Best when:** money movement that must be auditable, with deadlines and several flows ahead — which is this platform.

---

### Option D — External Saga / Workflow Engines

* **Temporal** — write the saga as a Java workflow; the engine provides durable state, retries, timers, and human tasks, so no `saga_instances` table or sweeper is built. **Cost:** a Temporal cluster to operate in Kubernetes + a new runtime dependency — heavier than the current lightweight posture (Redpanda chosen over Kafka partly for resource constraints, ADR-007) and hard to justify for two flows today. **Revisit if the saga count grows past ~3–4.**
* **Axon Framework (CQRS/event sourcing)** — native saga support, but requires an event-sourcing rewrite of existing CRUD entities and repositories. Architectural rewrite; **reject**.
* **Camunda / Zeebe (BPMN)** — full process engine; ops and learning overhead disproportionate for linear 3–4-step flows. **Reject.**

---

## 📊 Comparison Matrix

| Criterion | A — Patches | B — Choreographed | C — Orchestrated | D — Temporal |
|---|---|---|---|---|
| Distributed audit trail | ✗ | weak (scattered handlers) | ✓ (saga_instances) | ✓ (workflow history) |
| Automated compensation | partial (TTL job + runbook) | ✓ | ✓ | ✓ |
| Central timeout/deadline logic | ✗ | per-service sweepers | ✓ (sweeper) | ✓ (engine timers) |
| New components / ops | none | none | table + coordinator in payment-service | Temporal cluster |
| Fits existing outbox/gRPC/idempotency conventions | ✓ | ✓ | ✓ | foreign runtime |
| Code effort | ~1 week | ~1.5–2.5 weeks | ~2–4 weeks (phased) | ~2 weeks + infra |
| Control-flow testability | low | medium | high (pure FSM) | high (engine test kit) |
| Resume after crash | broker redelivery | local state + redelivery | DB-backed saga state | engine-durable |

---

## 🎯 Decision Guidance

Rule of thumb: with rare failures and 2–3 participants under independently owned services, **B** is defensible. For money movement that must be auditable, deadline-driven, and with several sagas ahead (this platform: factoring now, payment settlement next, ledger capture and recon already crossing `end_to_end_id`), **C** is the fit. **A** is a stopgap that still requires an operator for non-transient failures. **D** only pays off if the saga count multiplies.

Because the groundwork (compensation primitives, bus repairs, per-step idempotency, correlation) is option-independent, the pragmatic sequence is: **start the groundwork now, keep this decision open, then choose B vs C when the payment-settlement tail story is scheduled.** The remediation plan below is written for the recommended path (C), which subsumes most of B's required primitives anyway — choosing C later does not waste B-compatible work, since the compensation ops and idempotency are shared.

## Decision
**Deferred** — no option locked yet. Recommended when scheduled: **Option C (orchestrated saga)**, justified by: auditability of monetary flows, centralized timeouts, deterministic compensation order, direct reuse of existing ledger primitives (`HOLD_CANCEL`, ADR-003 reversals, outbox/SKIP LOCKED machinery), and multiple upcoming sagas sharing one runtime.

---

## 🛠️ Remediation Plan (Recommended Path: Option C)

Executed as phases; each phase is independently shippable and testable.

**Phase 0 — Option-independent groundwork** (see the Groundwork section): compensation primitives + event-bus repairs + idempotency/correlation hygiene. Land with unit/integration tests.

**Phase 1 — Participant compensating operations.**
* `smartpay-payment-service`: add `CANCELLED`/`FAILED` handling — today only `PAYMENT_INITIATED` exists and `getPaymentStatus` pins status to `INITIATED`; add `PAYMENT_CANCELLED` outbox event + status transition; wire the first real caller of `releaseHold(capture = false)`.
* `smartpay-invoice-service`: add the idempotent status-revert operation (`FACTORING_APPROVED` → `EPOD_VERIFIED`).
* `smartpay-payout-worker`: stop direct Kafka publish after side effects; emit a `BeginFactoringAdvance` request to the orchestrator instead.
* `smartpay-common`: add saga correlation fields to event records; consider a strongly-typed `SagaId` in `com.hozgan.smartpay.common.model.id.*` per repo conventions.

**Phase 2 — Saga runtime for `FACTORING_ADVANCE`.**
* Create `saga_instances` (DDL in Option C) in the orchestrator schema (`smartpay-payment-service`, `payment` schema — it owns the escrow/hold lifecycle, the outbox runtime, idempotency, and the ledger gRPC client; the alternative — giving the stateless payout worker its own DB + saga runtime — is more cost for no benefit).
* Implement the coordinator loop (begin / poll / onOutcome / sweeper / compensate) per the Option C pseudocode; FSM as a pure, unit-testable class.
* Commands via the orchestrator outbox worker; outcomes as enriched domain events; per-step idempotency keys.
* Contract: one live saga per `(saga_type, correlation_id)`; step commands/outcomes carry `sagaId` + `stepId`.

**Phase 3 — Timeouts & operations.**
* Sweeper: claim `RUNNING` sagas past `deadline_at` with `SKIP LOCKED` → `COMPENSATING`; exact-once claim under multi-instance deployment (test mirrors `OutboxWorkerConcurrencyTest`).
* `COMPENSATION_FAILED` state → alert + bounded backoff retry of the failed compensation; `saga_instances` remains the ops/audit query surface.

**Phase 4 — Payment settlement saga.**
* Build the outbox dispatch tail (bank rail → `ReleaseHold(capture = true)` → `PaymentSettledEvent`) as `SAGA-PAYMENT-SETTLE` on the same runtime, with the compensation matrix defined in Option C, instead of as unguarded sequential code.

**Testing strategy.**
* **Unit**: saga FSM transitions (pure state machine, no Spring) — every step-failure path, reverse-order compensation, terminal states.
* **Integration** (Testcontainers, per AGENTS.md): factoring saga happy path; force S1/S2 failure and assert compensation — `releaseHold(capture = false)` restores the available balance in the ledger slice, invoice returns to `EPOD_VERIFIED`, saga ends `COMPENSATED`; replay a compensation and assert no-op; crash the orchestrator mid-saga and assert resume from DB state.
* **Concurrency**: two sweeper instances claim the same overdue saga exactly once (`SKIP LOCKED`), mirroring `OutboxWorkerConcurrencyTest`.
* Full unit + integration suites green: `mvn test -Punit` and `mvn test -Pintegration` (per AGENTS.md).

---

## 🎯 Acceptance Criteria for Resolution
1. Any mid-flow failure in the factoring advance auto-compensates: the ledger hold is released (`releaseHold(capture = false)` restores available balance), the invoice returns to `EPOD_VERIFIED` (or the payment is cleanly `CANCELLED`), and no state remains stuck without an automated resolution path.
2. Every compensation is idempotent and safe to replay; a repeated compensation is a no-op.
3. A timeout sweeper moves `RUNNING` sagas past `deadline_at` to `COMPENSATING` exactly once under multi-instance deployment (`SKIP LOCKED`).
4. Saga state is durable (DB-backed, not in-memory) and survives orchestrator crashes; every saga command/outcome carries the saga correlation id.
5. `saga_instances` provides a queryable audit trail: type, correlation id, per-step status, compensations executed, and deadlines.
6. Unit + integration suites cover: happy path, failure at each step, double-compensation, and timeout claims — all green in `mvn test -Punit` and `mvn test -Pintegration`.
7. The payment-settlement tail (when built) is implemented as a saga with compensations from day one, not retrofitted.

---

## References (evidence)
* `smartpay-payment-service/.../service/PaymentService.java` — `initiatePayment` hold-then-commit sequence; only `PAYMENT_INITIATED` event type; status pinned to `INITIATED`.
* `smartpay-payment-service/.../service/OutboxEventPublisherWorker.java` — outbox rows only marked `processed_at`; javadoc defers real dispatch.
* `smartpay-payment-service/.../service/LedgerGrpcClient.java` — `releaseHold` defined, no production caller (verified repo-wide).
* `smartpay-payout-worker/.../service/FactoringPayoutWorker.java` — sequential side-effecting steps 3–5, no compensation.
* `smartpay-payout-worker/.../service/FactoringEventPublisher.java` — direct `kafkaTemplate.send(...).get(5s)` after side effects.
* `smartpay-payout-worker/.../client/PaymentGrpcClient.java` — per-invoice idempotency key `FACTORING-ADVANCE-INV-<invoiceId>`.
* `smartpay-ledger-service/.../service/AccountBalanceService.java` — `releaseHold(..., capture = false)` = `HOLD_CANCEL` restores available balance (integration-tested, no caller).
* `smartpay-invoice-service/.../service/EpodService.java` — event published only to in-process `ApplicationEventPublisher`; no Kafka producer in module.
* ADR-003 (append-only ledger, reversal postings), ADR-005 (outbox, `SKIP LOCKED`), ADR-008 (consumer-group concurrency) — building blocks the saga reuses.
