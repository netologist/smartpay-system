# ADR-008: Event-Driven Kafka Consumer Groups for Kubernetes Worker Concurrency

## Status
**ACCEPTED**

## Date
2026-09-06

## Context
The `smartpay-payout-worker` automates factoring advances: deducting a 2.5% platform fee and disbursing funds immediately upon freight delivery verification.

In Kubernetes environments, deploying this worker with horizontal redundancy (`replicas >= 2`) presents a concurrency challenge:
1. **The Polling Collision Antipattern (`@Scheduled`)**:
   If worker pods execute periodic polling (`GET /api/v1/invoices?status=EPOD_VERIFIED`), multiple pods simultaneously fetch the same batch of eligible invoices.
   Both pods attempt to disburse the advance using the same idempotency key (`FACTORING-ADV-INV-xxx`). While two-tier idempotency protects the financial ledger from double-spending, one pod always fails with `409 Conflict (IdempotencyConflictException)`, producing false-positive alerts, log pollution, and wasted compute resources.
2. **The Latency Problem of Kubernetes `CronJob`**:
   Deploying a single-pod Kubernetes `CronJob` with `concurrencyPolicy: Forbid` eliminates multi-pod collisions, but introduces batch scheduling latency (5 to 60 minutes). This directly compromises SmartPay's core value proposition of **"Instant Liquidity upon Delivery (< 1s)"**.

## Decision
Adopt **Event-Driven Kafka Consumer Groups** (`smartpay-factoring-workers`) over the `smartpay.events.invoice` topic, coupled with in-memory Java 25 Virtual Threads:

1. **Partition-Level Exclusive Assignment**:
   * Events (`EpodVerifiedEvent`) are published to Kafka partitioned by `carrier_id` or `invoice_id`.
   * Kafka's consumer group rebalance protocol guarantees that each partition is assigned exclusively to **one worker pod** at any given moment.
   * Competing pods never consume the same invoice event, mathematically eliminating multi-pod race conditions.
2. **Virtual Thread Offloading**:
   * Upon receiving a message, the worker immediately dispatches processing to an in-memory Virtual Thread (`Executors.newVirtualThreadPerTaskExecutor()`).
   * Outbound synchronous gRPC calls to `smartpay-risk-service` (`EvaluateCarrierRisk`) and `smartpay-payment-service` (`InitiatePayment`) block only the virtual thread, leaving the underlying carrier thread free.
3. **Offset Commit Semantics**:
   * Offsets are committed only after `smartpay-payment-service` confirms payment initiation (`201 Created` / `INITIATED`), ensuring strict At-Least-Once execution.

## Consequences
* **Positive**:
  * **Zero Multi-Pod Collisions**: Horizontal pod autoscaling (HPA) from 1 to $N$ pods without distributed locks (e.g. ShedLock, Redis, Zookeeper).
  * **Real-Time Instant Payouts**: Eliminates polling latency; disbursements trigger in $< 500\text{ ms}$ upon ePOD verification.
  * **Elastic Scalability**: Capacity scales dynamically with Kafka partition count.
* **Negative**:
  * Downstream payment services must enforce idempotency in case of Kafka consumer rebalance and message replay.
