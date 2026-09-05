# ADR-002: Selection of UUIDv7 (RFC 9562) for Database Primary Keys

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
Choosing a primary key strategy in a distributed PostgreSQL microservices architecture involves strict trade-offs between performance, security, and distribution:
* Sequential `BIGINT` (Serial/Identity): Introduces centralized database sequence dependencies, leaks transaction volume, and exposes enumeration attack vectors.
* `UUIDv4`: Completely random generation causes severe B-Tree index fragmentation, high page-splitting rates, cache churn, and write-ahead log (WAL) bloat under heavy insert workloads.
* `ULID`: Time-ordered, but lacks native PostgreSQL data type support; stored as `VARCHAR(26)` with higher storage costs (27+ bytes vs 16 bytes) and slower string indexing.

## Decision
Standardize on **UUIDv7 (RFC 9562)** for all distributed primary keys:
* Layout: 48-bit millisecond Unix Epoch timestamp in the most significant bits, followed by version (0111), variant (10), and 74 bits of cryptographically strong randomness.
* Native Integration: Implemented directly in `smartpay-common` (`com.hozgan.smartpay.common.util.UuidV7`) with zero external dependencies.

## Consequences
* **Positive**:
  * Native PostgreSQL 16-byte `uuid` storage efficiency.
  * Monotonically ascending time ordering eliminates B-Tree page splits and WAL bloat, delivering `BIGINT`-like append throughput.
  * Completely decentralized generation: independent microservices can generate millions of unique keys per second without database round-trips.
  * Built-in auditability: the exact millisecond creation timestamp can be extracted directly from the identifier (`UuidV7.extractTimestamp`).
* **Negative**:
  * Key values reveal creation time, which is intentional and advantageous for financial auditing.
