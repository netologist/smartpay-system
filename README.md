# SmartPay: Modern Logistics Payment, Double-Entry Ledger & Factoring Platform

[![Java 25](https://img.shields.io/badge/Java-25%20(Project%20Loom)-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![gRPC](https://img.shields.io/badge/gRPC-1.70.0-blueviolet.svg)](https://grpc.io/)
[![Redpanda](https://img.shields.io/badge/Redpanda-v24.2.4-red.svg)](https://redpanda.com/)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-1.4.1-lightgrey.svg)](https://www.archunit.org/)

SmartPay is a high-performance, event-driven financial logistics payment platform built with **Java 25 (Virtual Threads)** and **Spring Boot 4.1**, tailored for UK and European freight transport. It delivers immutable double-entry bookkeeping, cryptographic electronic proof of delivery (ePOD) verification, automated freight invoice pricing, instant carrier factoring liquidity, and ISO-20022 bank statement auto-reconciliation.

---

## 📑 Documentation Index

Comprehensive architecture models, decision records, and developer story cards are organized under `docs/`:

* 🏛️ [**C4 Architecture Models (Context, Container, Component, Code)**](docs/architecture/c4-architecture-models.md)
* 🗺️ [**High-Level Architecture & Bounded Contexts**](docs/architecture/high-level-architecture.md)
* ⚙️ [**Low-Level Design & Concurrency / Zero-Sum Algorithms**](docs/architecture/low-level-architecture.md)
* 🔄 [**Sequence Diagrams**](docs/architecture/sequence-diagrams.md)
* 👥 [**Use Case Diagrams**](docs/architecture/usecase-diagrams.md)
* 📘 [**gRPC & Protocol Buffers Technical Guide**](docs/architecture/grpc-technical-guide.md)
* 📜 [**Architecture Decision Records (ADR-001 - ADR-007)**](docs/decisions/README.md)
* 📑 [**OpenAPI 3.1 REST Specifications**](docs/openapi/README.md)
* 📋 [**Developer Story Cards (STORY-001 - STORY-006)**](docs/stories/README.md)
* ⚠️ [**Technical Debt Records (TD-001: Currency Master)**](docs/tech-debt/TD-001-currency-definitions-master-table.md)

---

## 🌐 Service Port Matrix (Port Mappings)

| Service | Module | HTTP Port | gRPC Port | Database | Responsibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `smartpay-gateway` | `8080` | — | `smartpay_db` | Reverse proxy, JWT auth, rate limiting, two-tier SHA-256 idempotency |
| **Ledger Service** | `smartpay-ledger-service` | `8081` | `9091` | `smartpay_db` | Double-entry journal posting, balance transfers, hold/release |
| **Payment Service** | `smartpay-payment-service` | `8082` | `9092` | `smartpay_db` | Faster Payments / VRP orchestration, Transactional Outbox |
| **Invoice Service** | `smartpay-invoice-service` | `8083` | `9093` | `smartpay_db` | ePOD signature verification, freight pricing (base + fuel + VAT) |
| **Recon Service** | `smartpay-recon-service` | `8084` | `9094` | `smartpay_db` | CAMT.053 XML / MT940 bank statement reconciliation engine |
| **Risk Service** | `smartpay-risk-service` | `8085` | `9095` | `smartpay_db` | Fraud detection and carrier/shipper credit risk assessment |
| **Notification Svc** | `smartpay-notification-service`| `8086` | `9096` | `smartpay_db` | Event-driven Email / SMS notification dispatcher |
| **Payout Worker** | `smartpay-payout-worker` | `8087` | — | `smartpay_db` | Virtual Thread factoring payout background worker |
| **PostgreSQL 16** | `postgres` | `5432` | — | `smartpay_db` | Primary ACID database with B-Tree UUIDv7 indexes |
| **Redpanda (Kafka API)** | `redpanda` | `9092` | — | — | Lightweight C++20 event streaming broker (ADR-007) |
| **Redpanda Console**| `redpanda-console` | `8090` | — | — | Topic and message monitoring dashboard (`http://localhost:8090`) |

---

## 🛠️ Local Environment Setup

### 1. Prerequisites
* **Java 25**:
  ```bash
  # Via SDKMAN:
  sdk install java 25-open
  # Or via mise:
  mise use java@25
  # Verify:
  java -version # Must output OpenJDK 25
  ```
* **Apache Maven 3.9+**:
  ```bash
  mvn -version # Must output Apache Maven 3.9.x
  ```
* **Docker & Docker Compose**:
  ```bash
  docker compose version
  ```

---

### 2. Start Infrastructure (Docker Compose)
Launch PostgreSQL 16, Redpanda (lightweight Kafka broker), and Redpanda Console:
```bash
docker compose up -d
```

Verify container health:
```bash
docker compose ps
```
* **PostgreSQL 16**: `localhost:5432` (User: `smartpay_admin`, Password: `smartpay_secret`, DB: `smartpay_db`)
* **Redpanda (Kafka Wire Protocol)**: `localhost:9092` (Spring Boot connects directly)
* **Redpanda Console UI**: Open `http://localhost:8090` in browser

---

### 3. Generate gRPC Contracts & Compile Project
First, compile `.proto` contract files to generate Java message and stub classes:
```bash
mvn compile -pl smartpay-proto
```

Then compile the entire multi-module project:
```bash
mvn test-compile
```

---

### 4. Run Unit and Architecture Tests (ArchUnit)
Execute unit tests for Money, typed IDs, converters, and ArchUnit modern architecture fitness functions:
```bash
mvn clean test -pl smartpay-common
```
*(All 63 tests must pass with 0 failures)*

---

### 5. Run Microservices
Each service can be started independently via the Spring Boot Maven plugin:

```bash
# 1. Start Ledger Service (HTTP: 8081, gRPC: 9091)
mvn spring-boot:run -pl smartpay-ledger-service

# 2. Start Invoice & ePOD Service (HTTP: 8083)
mvn spring-boot:run -pl smartpay-invoice-service

# 3. Start Payment Service (HTTP: 8082)
mvn spring-boot:run -pl smartpay-payment-service

# 4. Start API Gateway (HTTP: 8080)
mvn spring-boot:run -pl smartpay-gateway
```

---

## 🗄️ Database Schemas & Flyway Migrations

Flyway migration scripts are maintained in `src/main/resources/db/migration/` across services:

* **`V1__init_accounts_and_balances.sql`** (`smartpay-ledger-service`):
  * `accounts` table (Chart of Accounts, currency).
  * `account_balances` table (Cleared balance, hold balance, `@Version` optimistic lock).
* **`V2__init_double_entry_ledger.sql`** (`smartpay-ledger-service`):
  * `journal_transactions` table (Transaction header, idempotency key).
  * `journal_entries` table (Immutable debit/credit lines with UPDATE/DELETE trigger guard).
* **`V3__init_invoicing_and_epod.sql`** (`smartpay-invoice-service`):
  * `epod_records` table (ePOD GPS coordinates, S3 photo URL, SHA-256 signature hash).
  * `invoices` table (Freight invoices, base amount, 12% fuel surcharge, 20% VAT, vehicle type, multi-currency).
* **`V4__init_transactional_outbox.sql`** (`smartpay-payment-service`):
  * `transactional_outbox` table (`SKIP LOCKED` indexed At-Least-Once event store).
* **`V5__init_idempotency_records.sql`** (`smartpay-payment-service` & `smartpay-gateway`):
  * `idempotency_records` table (Two-tier distributed lock with SHA-256 request body hash).
* **`V6__init_bank_reconciliation.sql`** (`smartpay-recon-service`):
  * `bank_statements` & `bank_statement_lines` (CAMT.053 / MT940 statement lines).

---

## 🚀 Developer Roadmap (Story-by-Story)

Follow the structured story cards to implement domain logic and services:

1. [**STORY-001: Double-Entry Ledger & Atomic Balance Transfer Engine**](docs/stories/STORY-001-ledger-double-entry-engine.md) *(Start here)*
2. [**STORY-002: Freight Invoicing & ePOD Pricing Engine**](docs/stories/STORY-002-invoice-epod-pricing-engine.md)
3. [**STORY-003: Payment Initiation & Transactional Outbox**](docs/stories/STORY-003-payment-initiation-outbox.md)
4. [**STORY-004: Carrier Factoring & Instant Payout Worker**](docs/stories/STORY-004-payout-factoring-worker.md)
5. [**STORY-005: Bank Statement & Auto-Reconciliation Engine**](docs/stories/STORY-005-bank-reconciliation-engine.md)
6. [**STORY-006: API Gateway & Distributed Idempotency Filter**](docs/stories/STORY-006-api-gateway-idempotency.md)

Refer to the [**gRPC Technical Guide**](docs/architecture/grpc-technical-guide.md) for stub usage, protobuf mapping, and error handling.
