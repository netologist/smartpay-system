# ADR-005: Transactional Outbox Pattern with PostgreSQL SKIP LOCKED

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
A major challenge in distributed architectures is the "dual-write" dilemma:
```java
// DANGEROUS DUAL-WRITE ANTIPATTERN:
paymentRepository.save(payment);       // Committed to DB
kafkaTemplate.send("payments", event); // Network fails / broker down -> EVENT LOST!
```
Writing to a database and publishing to a message broker in an uncoordinated manner leads to state divergence. Implementing distributed two-phase commit (2PC / XA) between PostgreSQL and Kafka is prohibitively slow, complex, and brittle.

## Decision
Adopt the **Transactional Outbox Pattern** with PostgreSQL row-level locking:
1. **Outbox Table (`transactional_outbox`)**: Domain state changes and the corresponding integration event payload (`JSONB`) are saved within the same local database transaction. ACID guarantees that the event is committed if and only if the business data is committed.
2. **PostgreSQL SKIP LOCKED Polling**:
   ```sql
   SELECT * FROM transactional_outbox
   WHERE processed_at IS NULL
   ORDER BY created_at ASC
   LIMIT 50
   FOR UPDATE SKIP LOCKED;
   ```
   Multiple Virtual Thread worker instances can poll the table concurrently without lock contention or thread blocking.
3. **At-Least-Once Delivery**: Events are published to Kafka and marked `processed_at = CURRENT_TIMESTAMP`.

## Consequences
* **Positive**: Zero event loss guarantee, resilience against broker downtime, high-throughput concurrent polling.
* **Negative**: Downstream consumers must handle duplicate events idempotently (at-least-once semantics).
