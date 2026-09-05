# SmartPay Project Conventions & Agent Guidelines

## 🌐 Language Policy (Strict Rule)
* **All outputs, documentation, code comments, commit messages, pull requests, issue descriptions, and assistant responses MUST strictly be in English.**
* Even if the user interacts or asks questions in Turkish (or any other language), the response, code, and artifacts generated must be delivered in **English**.

---

## 🏗️ Technical & Architectural Standards

### 1. Platform & Toolchain
* **Java Version**: Modern **Java 25** with Virtual Threads (Project Loom).
* **Framework**: **Spring Boot 4.1**.
* **Database**: **PostgreSQL 16** with native 16-byte B-Tree `UUID` support.
* **Message Broker**: **Redpanda** (Kafka API wire-compatible) on port `9092` locally; Apache Kafka / AWS MSK in production.
* **Internal RPC**: **gRPC over HTTP/2** with Protocol Buffers (`smartpay-proto`).
* **Public Ingress**: **API Gateway** (`smartpay-gateway`) with REST (OpenAPI 3.1) and JWT bearer authentication.

### 2. Domain & Coding Rules
* **Immutability & Records**: All domain value objects, DTOs, events, and IDs must be implemented as immutable Java `record` types.
* **Double-Entry General Ledger**:
  * Every transaction must strictly adhere to the zero-sum invariant: $\sum \text{Debit} == \sum \text{Credit}$.
  * Ledger entries (`journal_entries`) are strictly **append-only**; never issue `UPDATE` or `DELETE`.
* **Primary Keys**: Always generate **UUIDv7** (`com.hozgan.smartpay.common.util.UuidV7`) for time-ordered, deadlock-free, B-Tree index-friendly identifiers.
* **Monetary Calculations**: Always use `com.hozgan.smartpay.common.model.Money`. Never use floating-point types (`double`/`float`) for money.
* **Concurrency & Virtual Threads**:
  * Never use `synchronized` methods or blocks (prevents carrier thread pinning in Project Loom).
  * Use `ReentrantLock` or immutable records.
  * For background task pools, use `Executors.newVirtualThreadPerTaskExecutor()`.
* **Pessimistic Locking**: Acquire locks on accounts in strictly ascending `AccountId` order to eliminate deadlocks.
* **Distributed Idempotency**: All mutation endpoints must enforce two-tier idempotency with SHA-256 request body hashing.
* **Transactional Outbox**: Never perform dual-writes (DB + Kafka). Commit domain changes and outbox events in the same local transaction, polling via `SKIP LOCKED`.

---

## 🧪 Testing Standards
* Every domain component must be backed by unit tests using **JUnit 5** and **AssertJ**.
* Architectural rules must be validated with **ArchUnit** (`ArchitectureTest.java`).
* Run tests with:
  ```bash
  mvn clean test -pl smartpay-common
  ```
