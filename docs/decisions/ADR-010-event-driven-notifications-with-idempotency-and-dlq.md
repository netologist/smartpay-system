# ADR-010: Event-Driven Multi-Channel Notifications with Consumer Idempotency and DLQ

## Status
**ACCEPTED**

## Date
2026-09-07

## Context
The `smartpay-notification-service` is responsible for customer communications across the freight financing lifecycle:
* Instant SMS dispatch upon payment settlement to carrier bank accounts (`PaymentSettledEvent`).
* Formal freight invoice email delivery to shippers (`InvoiceIssuedEvent`).
* Instant liquidity advance approval notices to carriers (`FactoringPayoutApprovedEvent`).

Delivering financial notifications introduces three critical architectural challenges:
1. **Duplicate Notification Spam (The Rebalance Antipattern)**:
   In distributed message brokers (Kafka/Redpanda), consumer rebalances, network timeouts, or pod restarts trigger message replay. Sending duplicate SMS or Email notifications regarding fund disbursements damages customer trust and incurs provider costs.
2. **Third-Party Latency & Thread Starvation**:
   External communication providers (Twilio, SendGrid, carrier webhooks) have variable HTTP response times ($200\text{ ms} - 5\text{ s}$). Blocking Kafka listener consumer threads on external REST calls degrades event consumption throughput and risks consumer group eviction due to missed heartbeats (`max.poll.interval.ms`).
3. **Provider Outages & Cascading Failures**:
   If an external provider suffers an outage (e.g. Twilio HTTP 500 or 429 rate limiting), messages must be retried with exponential backoff and ultimately routed to a Dead Letter Queue (DLQ) without crashing the consumer loop.

## Decision
Adopt an **Event-Driven Asynchronous Notification Engine** combining database-level idempotency, Java 25 Virtual Threads, Resilience4j fault tolerance, and Kafka Dead Letter Queues:

1. **Two-Tier Consumer Idempotency**:
   * **Database Invariant**: Table `notification_logs` enforces `CONSTRAINT uq_notification_event_channel UNIQUE (event_id, channel)`.
   * **Pre-Dispatch Verification**: Before invoking external providers, `NotificationIdempotencyService` queries `notification_logs` for an existing log with status `DISPATCHED`. If found, duplicate dispatch is suppressed and the offset is committed immediately.
2. **Project Loom Virtual Thread Offloading**:
   * Incoming Kafka records are offloaded to Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
   * External HTTP calls use `RestClient` backed by `JdkClientHttpRequestFactory`, ensuring zero carrier thread pinning and high dispatch concurrency.
3. **Resilience4j Fault Tolerance & Exponential Backoff**:
   * External provider calls are decorated with Resilience4j `@Retry` (3 maximum attempts, 200ms initial wait, 2x multiplier) and `@CircuitBreaker`.
   * Transient errors (HTTP 5xx, HTTP 429) trigger retries; permanent client errors (HTTP 4xx) fail immediately.
4. **Dead Letter Queue (DLQ) Routing**:
   * When provider retries are exhausted, the notification log is marked as `DEAD_LETTERED`.
   * The failed payload is published to `smartpay.events.notifications.dlq` via `NotificationDlqPublisher` with error diagnostics.
   * The consumer offset is committed to prevent poison pill message loops.

## Consequences
* **Positive**:
  * **Zero Duplicate Dispatches**: Strict deduplication guarantees that replayed Kafka events never trigger duplicate SMS or Email messages.
  * **Non-Blocking Throughput**: Virtual threads isolate external provider latency from Kafka consumer heartbeat cycles.
  * **Operational Visibility**: Full audit trail in `notification_logs` tracking message status (`PENDING`, `DISPATCHED`, `FAILED`, `DEAD_LETTERED`) and provider message IDs.
* **Negative**:
  * Requires database writes (`notification_logs`) before and after provider dispatches, adding minor database load.
