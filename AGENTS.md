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
* **Object Mapping**: **MapStruct 1.6.3** (`@Mapper(componentModel = "spring")`) for compile-time, type-safe entity/DTO transformations.

### 2. Domain & Coding Rules
* **Package Structure Standardization (`.web`)**:
  * All HTTP endpoints, REST controllers, and global exception handlers in microservices must reside under the `.web` package (e.g. `com.hozgan.smartpay.ledger.web`, `com.hozgan.smartpay.invoice.web`).
  * Never use ad-hoc package names like `.controller`.
* **Immutability & Records**:
  * All domain value objects, DTOs, events, and IDs must be implemented as immutable Java `record` types.
* **Layered Monetary Precision Standards**:
  * **Database & Entity Layer**: Monetary values must be stored in minor units (`BIGINT` in pence/cents) with an explicit ISO-4217 currency code (`VARCHAR(3)`). Never use floating-point types (`double`/`float`) in the database.
  * **Service & Application Layer**: Business logic, services, domain models, and use cases must strictly operate on rich `com.hozgan.smartpay.common.model.Money` value objects (and composite domain models like `InvoicePricing`).
  * **DTO / Transport Layer**: DTOs should leverage rich `Money` objects directly, exposing `@JsonProperty` getters for minor units (pence) to provide dual representation for machine-friendly web and mobile clients.
* **JSON Serialization & Deserialization (`@JacksonComponent`)**:
  * `Money` value objects must be serialized and deserialized using Spring Boot 4.1's `@JacksonComponent` (`com.hozgan.smartpay.common.jackson.MoneyJsonComponent` via `ObjectValueSerializer<Money>` and `ObjectValueDeserializer<Money>`).
  * Domain models (`Money.java`) must remain pure Java records free of direct framework annotations (`tools.jackson.*`), enforced by ArchUnit architecture fitness rules.
* **Object Mapping Standards (MapStruct)**:
  * Transformations between Entities, Domain Models, and DTOs must use **MapStruct** (`@Mapper(componentModel = "spring")`).
  * Never write repetitive manual copying logic inside controllers or services.
* **Double-Entry General Ledger**:
  * Every transaction must strictly adhere to the zero-sum invariant: $\sum \text{Debit} == \sum \text{Credit}$.
  * Ledger entries (`journal_entries`) are strictly **append-only**; never issue `UPDATE` or `DELETE`.
* **Primary Keys**:
  * Always generate **UUIDv7** (`com.hozgan.smartpay.common.util.UuidV7`) for time-ordered, deadlock-free, B-Tree index-friendly identifiers.
* **Concurrency & Virtual Threads**:
  * Never use `synchronized` methods or blocks (prevents carrier thread pinning in Project Loom).
  * Use `ReentrantLock` or immutable records.
  * For background task pools, use `Executors.newVirtualThreadPerTaskExecutor()`.
* **Pessimistic Locking**:
  * Acquire locks on accounts in strictly ascending `AccountId` order to eliminate deadlocks.
* **Distributed Idempotency**:
  * All mutation endpoints must enforce two-tier idempotency with SHA-256 request body hashing.
* **Transactional Outbox**:
  * Never perform dual-writes (DB + Kafka). Commit domain changes and outbox events in the same local transaction, polling via `SKIP LOCKED`.

---

## 🧪 Testing Standards & Lifecycle Execution

### 1. Test Partitioning & Tags
* Every test class must be explicitly tagged using JUnit 5 tags:
  * **`@Tag("unit")`**: Pure in-memory unit tests (domain models, pricing math, MapStruct mappers, ArchUnit architecture fitness functions). Must execute in $<10\text{s}$ with zero Docker/container footprint.
  * **`@Tag("integration")`**: Full-stack integration and slice tests (PostgreSQL 16 Testcontainers, HTTP/2 gRPC channels on Virtual Threads, MockMvc slices, pessimistic locking concurrency suites).
* Physical folder-based test separation (`src/test/java` vs `src/it/java`) is recorded as technical debt (`docs/tech-debt/TD-002-test-directory-physical-partitioning.md`) for future phased migration.

### 2. Maven Test Commands
```bash
# 1. Run ALL tests (default across all active modules):
mvn test -pl smartpay-common,smartpay-ledger-service,smartpay-invoice-service

# 2. Run ONLY fast unit tests (in-memory, no Docker containers, ~6-10s):
mvn test -Punit
# (Alternative CLI syntax: mvn test -Dgroups=unit)

# 3. Run ONLY integration tests (Testcontainers PostgreSQL 16, gRPC, MockMvc):
mvn test -Pintegration
# (Alternative CLI syntax: mvn test -Dgroups=integration)
```
