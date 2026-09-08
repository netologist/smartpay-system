# SmartPay: Modern Logistics Payment, Double-Entry Ledger & Factoring Platform

[![Java 25](https://img.shields.io/badge/Java-25%20(Project%20Loom)-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![gRPC](https://img.shields.io/badge/gRPC-1.70.0-blueviolet.svg)](https://grpc.io/)
[![Redpanda](https://img.shields.io/badge/Redpanda-v24.2.4-red.svg)](https://redpanda.com/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.0-yellow.svg)](https://resilience4j.readme.io/)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-1.4.1-lightgrey.svg)](https://www.archunit.org/)
[![JaCoCo](https://img.shields.io/badge/JaCoCo-0.8.15-success.svg)](https://www.jacoco.org/)
[![Spotless](https://img.shields.io/badge/Spotless-2.44.3-informational.svg)](https://github.com/diffplug/spotless)
[![Docker](https://img.shields.io/badge/Distroless-Non--Root%20UID%2010001-blue.svg)](https://github.com/GoogleContainerTools/distroless)

---

## 📖 What is SmartPay? (Project Overview for First-Time Readers)

### 🚛 The Real-World Industry Problem
In UK and European commercial freight logistics, transport operators and independent carriers face crippling cashflow bottlenecks:
1. **Net-30 to Net-90 Payment Terms**: Shippers typically settle freight invoices 30 to 90 days after delivery. Carriers, however, must immediately pay for fuel, truck leases, and driver wages.
2. **Fragile Paperwork & Disputes**: Traditional proof of delivery (POD) involves physical paper notes that get lost, delayed, or disputed, delaying payments for weeks.
3. **Double-Spending & Reconciliation Chaos**: Financial teams manually match bank statement lines to invoices and ledger accounts, resulting in reconciliation backlogs, audit risk, and accounting discrepancies.

### 💡 The SmartPay Solution
**SmartPay** is an enterprise-grade, event-driven financial logistics platform that automates the entire invoice-to-settlement lifecycle in **sub-second real-time**:

```
[ Cargo Delivery ] ──▶ [ Cryptographic ePOD ] ──▶ [ Automated Invoice ] ──▶ [ Instant Factoring Payout (<1s) ] ──▶ [ Bank Statement Recon ]
```

1. **Cryptographic Electronic Proof of Delivery (ePOD)**:
   Drivers record delivery completions with GPS geolocation, delivery photos, and immutable SHA-256 cryptographic signature digests.
2. **Automated Freight Pricing Engine**:
   Invoices are generated dynamically based on vehicle type (Articulated Lorry, Rigid Truck, Van), mileage rates, dynamic fuel surcharges (12%), and statutory VAT (20%) using precision `Money` value objects.
3. **Instant Carrier Factoring Liquidity (< 1s Payout)**:
   Upon ePOD verification, carriers receive instant factoring liquidity (gross invoice minus a 2.5% platform fee) disbursed directly over bank rails (Faster Payments / VRP) without waiting 30–90 days.
4. **Luca Pacioli Double-Entry Ledger**:
   An immutable, append-only financial general ledger enforcing the strict zero-sum invariant: $\sum \text{Debits} == \sum \text{Credits}$. Balances are guarded against double-spending and deadlocks via strict account-order pessimistic locking.
5. **Two-Tier Distributed Idempotency**:
   Cryptographic SHA-256 request fingerprinting paired with database locks ensures that client retries or network replays never produce duplicate withdrawals or payments.
6. **Automated Bank Reconciliation**:
   Parses ISO 20022 CAMT.053 XML and SWIFT MT940 bank statement feeds, auto-matching statement entries against ledger records via unique `end_to_end_id` tokens.

---

## 🏗️ System Architecture & Event Flow

The platform is structured into bounded contexts interacting via **gRPC over HTTP/2** for synchronous internal RPC, **REST (OpenAPI 3.1)** for public ingress, and **Redpanda (Kafka API)** for event streaming.

```mermaid
flowchart TD
    subgraph Clients["External Actors & Clients"]
        Shipper["🏢 Shipper Web Portal"]
        Carrier["🚚 Carrier / Driver Mobile App"]
        Bank["🏦 UK Banking Rails (Faster Payments / CAMT.053)"]
        Auditor["📊 Financial Auditor / Finance Ops"]
    end

    subgraph Ingress["Ingress Layer"]
        Gateway["smartpay-gateway (Port 8080)<br/>• Reverse Proxy & JWT Auth<br/>• Rate Limiting & Two-Tier Idempotency"]
    end

    subgraph CoreServices["Microservices (Modern Java 25 & Spring Boot 4.1)"]
        InvoiceSvc["smartpay-invoice-service (Port 8083)<br/>• ePOD Signature Verification<br/>• Freight Pricing Engine (Base + Fuel + VAT)"]
        RiskSvc["smartpay-risk-service (Port 8085 / gRPC 9095)<br/>• Carrier Credit Scoring<br/>• Fraud Propensity Evaluation"]
        PayoutWorker["smartpay-payout-worker (Port 8087)<br/>• 2.5% Factoring Engine<br/>• Virtual Thread Background Worker<br/>• Resilience4j Circuit Breaker & Retry"]
        PaymentSvc["smartpay-payment-service (Port 8082 / gRPC 9092)<br/>• Payment Orchestration<br/>• Transactional Outbox Engine"]
        LedgerSvc["smartpay-ledger-service (Port 8081 / gRPC 9091)<br/>• Double-Entry Journal Engine<br/>• Pessimistic Locking & Balances"]
        ReconSvc["smartpay-recon-service (Port 8084 / gRPC 9094)<br/>• CAMT.053 / MT940 Parser<br/>• Auto-Reconciliation Engine"]
        NotificationSvc["smartpay-notification-service (Port 8086)<br/>• Multi-Channel Dispatcher (Email/SMS)<br/>• Consumer Idempotency"]
    end

    subgraph DataInfrastructure["Data & Messaging Infrastructure"]
        Postgres[("🐘 PostgreSQL 16 (Port 5432)<br/>• UUIDv7 B-Tree Primary Keys<br/>• Append-Only Journal Entries<br/>• SKIP LOCKED Outbox Tables")]
        Redpanda{{"🐼 Redpanda / Kafka (Port 9092)<br/>• smartpay.events.invoice<br/>• smartpay.events.factoring<br/>• smartpay.events.payment"}}
    end

    %% External Connections — every external request enters via the gateway only
    Shipper -->|HTTPS REST| Gateway
    Carrier -->|HTTPS REST / ePOD| Gateway
    Bank -->|Bank Statements / Payout Clearance| Gateway
    Auditor -->|Audit / Recon Reports| Gateway

    %% Gateway Routing (sole public entry; path passthrough)
    Gateway -->|"/api/v1/invoices, /api/v1/epod"| InvoiceSvc
    Gateway -->|"/api/v1/payments"| PaymentSvc
    Gateway -->|"/api/v1/notifications"| NotificationSvc
    Gateway -->|"/api/v1/recon"| ReconSvc

    %% Core Service Interactions
    InvoiceSvc -->|Publish EpodVerifiedEvent| Redpanda
    InvoiceSvc -.-> Postgres

    Redpanda -->|Consume EpodVerifiedEvent| PayoutWorker
    PayoutWorker -->|1. REST Fetch Invoice| InvoiceSvc
    PayoutWorker -->|2. gRPC EvaluateRisk| RiskSvc
    PayoutWorker -->|3. gRPC InitiatePayment| PaymentSvc
    PayoutWorker -->|4. REST Update Status| InvoiceSvc
    PayoutWorker -->|5. Publish FactoringPayoutApprovedEvent| Redpanda

    PaymentSvc -->|gRPC HoldFunds / ReleaseHold| LedgerSvc
    PaymentSvc -->|Transactional Outbox Commit| Postgres
    PaymentSvc -->|Publish PaymentEvents| Redpanda

    LedgerSvc -.-> Postgres
    ReconSvc -.-> Postgres

    Redpanda -->|Consume Domain Events| NotificationSvc
```

---

## 📑 Complete Documentation Hub

Comprehensive architectural specifications, design decision records, and story cards are organized under `docs/`:

### 🏛️ Architecture & Deep-Dive Technical Guides
* 🗺️ [**High-Level Architecture & Bounded Contexts**](docs/architecture/high-level-architecture.md): DDD bounded contexts, strategic domain boundaries, and integration protocols.
* ⚙️ [**Low-Level Architecture & Algorithms**](docs/architecture/low-level-architecture.md): Concurrency control, pessimistic lock ordering, Luca Pacioli zero-sum balance algorithm, and UUIDv7 indexing.
* 🏛️ [**C4 Architecture Models (Context, Container, Component, Code)**](docs/architecture/c4-architecture-models.md): Complete C4 model diagrams and container interactions.
* 🔄 [**Sequence Diagrams**](docs/architecture/sequence-diagrams.md): End-to-end execution sequences for ePOD delivery, factoring advances, balance transfers, and bank reconciliations.
* 👥 [**Use Case Diagrams**](docs/architecture/usecase-diagrams.md): Actor use case boundaries across Shippers, Carriers, Bank Rails, and Finance Ops.
* 📘 [**gRPC & Protocol Buffers Technical Guide**](docs/architecture/grpc-technical-guide.md): Protobuf contract specifications, client stubs, deadlines, and error handling.
* ⚖️ [**Containerization, Tooling & Kubernetes Tradeoffs**](docs/architecture/containerization-and-tooling-tradeoffs.md): Deep-dive tradeoff matrix across container engines, JVM virtual thread tuning, database migrations, and testing topologies.

---

### 📜 Architecture Decision Records (ADRs)
* 📑 [**ADR Index & Architecture Governance**](docs/decisions/README.md)
* [**ADR-001: Adoption of Modern Java 25 & Spring Boot 4.1**](docs/decisions/ADR-001-modern-java-25-and-spring-boot-4.md)
* [**ADR-002: UUIDv7 Primary Keys for Time-Ordered Database Performance**](docs/decisions/ADR-002-uuidv7-primary-keys.md)
* [**ADR-003: Double-Entry Immutable General Ledger Engine**](docs/decisions/ADR-003-double-entry-immutable-ledger.md)
* [**ADR-004: gRPC over HTTP/2 for Internal Microservice Communication**](docs/decisions/ADR-004-grpc-internal-service-communication.md)
* [**ADR-005: Transactional Outbox Pattern for At-Least-Once Delivery**](docs/decisions/ADR-005-transactional-outbox-event-driven.md)
* [**ADR-006: Two-Tier Distributed Idempotency with SHA-256 Fingerprinting**](docs/decisions/ADR-006-two-tier-distributed-idempotency.md)
* [**ADR-007: Redpanda for C++20 Kafka-Compatible Event Streaming**](docs/decisions/ADR-007-redpanda-for-local-development.md)
* [**ADR-008: Event-Driven Kafka Consumer Groups for Kubernetes Concurrency**](docs/decisions/ADR-008-event-driven-worker-concurrency-in-kubernetes.md)
* [**ADR-009: Multi-Stage Distroless Containerization with Custom JRE**](docs/decisions/ADR-009-multi-stage-distroless-containerization.md)
* [**ADR-010: Event-Driven Multi-Channel Notifications with Consumer Idempotency and DLQ**](docs/decisions/ADR-010-event-driven-notifications-with-idempotency-and-dlq.md)
* [**ADR-011: Automated Multi-Stage Quality Gates & Test Partitioning**](docs/decisions/ADR-011-automated-quality-gates-and-test-partitioning.md)

---

### 📑 API Specifications (OpenAPI 3.1 & Protocol Buffers)
* 🌐 [**OpenAPI Specifications Overview**](docs/openapi/README.md)
* 🚪 [**API Gateway Public Ingress (OpenAPI 3.1)**](docs/openapi/gateway-api.yaml)
* 📖 [**Double-Entry General Ledger API (OpenAPI 3.1)**](docs/openapi/ledger-service-api.yaml)
* 📄 [**Freight Invoicing & ePOD Service API (OpenAPI 3.1)**](docs/openapi/invoice-service-api.yaml)
* 💳 [**Payment Orchestration Service API (OpenAPI 3.1)**](docs/openapi/payment-service-api.yaml)
* 🏦 [**Bank Reconciliation Service API (OpenAPI 3.1)**](docs/openapi/recon-service-api.yaml)
* 🔔 [**Multi-Channel Notification Engine API (OpenAPI 3.1)**](docs/openapi/notification-service-api.yaml)
* 📦 [**Protocol Buffers Contracts (`smartpay-proto`)**](smartpay-proto/src/main/proto/) (`ledger.proto`, `payment.proto`, `risk.proto`, `common.proto`)

---

### 📋 Feature Stories & Engineering Epics
* 🗂️ [**Story Roadmap, Sequence & Dependency Matrix**](docs/stories/README.md)
* ✅ [**STORY-001: Double-Entry Ledger & Atomic Transfer Engine**](docs/stories/STORY-001-ledger-double-entry-engine.md)
* ✅ [**STORY-002: Freight Invoicing & ePOD Pricing Engine**](docs/stories/STORY-002-invoice-epod-pricing-engine.md)
* ✅ [**STORY-003: Payment Initiation & Transactional Outbox**](docs/stories/STORY-003-payment-initiation-outbox.md)
* ✅ [**STORY-004: Carrier Factoring & Instant Payout Worker**](docs/stories/STORY-004-payout-factoring-worker.md)
* ✅ [**STORY-005: Bank Statement & Auto-Reconciliation Engine**](docs/stories/STORY-005-bank-reconciliation-engine.md)
* ✅ [**STORY-006: API Gateway & Distributed Idempotency Filter**](docs/stories/STORY-006-api-gateway-idempotency.md)
* ✅ [**STORY-007: Carrier Credit Risk & Fraud Engine**](docs/stories/STORY-007-carrier-risk-fraud-engine.md)
* ✅ [**STORY-008: Event-Driven Multi-Channel Notification Engine**](docs/stories/STORY-008-event-driven-notifications.md)
* ✅ [**TECH-001: Containerization & K8s Kustomize Engine**](docs/stories/TECH-001-containerization-kustomize-manifests.md)
* ✅ [**TECH-002: Enterprise CI Pipeline Automation**](docs/stories/TECH-002-ci-pipeline-automation.md)
* ✅ [**TECH-003: KinD Cluster & E2E Testing Pipeline**](docs/stories/TECH-003-kind-e2e-testing-pipeline.md)
* ⏳ [**TECH-004: Envoy Service Mesh & Zero-Trust mTLS**](docs/stories/TECH-004-envoy-service-mesh-mtls.md)

---

### ⚠️ Technical Debt & Engineering Standards
* 🏛️ [**Agent & Architectural Coding Standards**](AGENTS.md)
* ⚠️ [**TD-001: Currency Master Definitions Table**](docs/tech-debt/TD-001-currency-definitions-master-table.md)
* ⚠️ [**TD-002: Test Directory Physical Partitioning**](docs/tech-debt/TD-002-test-directory-physical-partitioning.md)
* ⚠️ [**TD-003: Wire Money as `amount` + `currency` Object (not pence)**](docs/tech-debt/TD-003-money-amount-currency-dto.md)

---

## 🌐 Service Port Matrix

| Service | Module | HTTP Port | gRPC Port | Database (Schema) | Primary Responsibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `smartpay-gateway` | `8080` | — | `smartpay_db` (`gateway`) | **Sole public entry**. Reverse proxy, JWT auth, rate limiting, two-tier SHA-256 idempotency. Routes (`path passthrough`): `/api/v1/payments`, `/api/v1/invoices`, `/api/v1/epod`, `/api/v1/notifications`, `/api/v1/recon`. Downstream targets injected in-cluster via `SMARTPAY_PAYMENT_SERVICE_URL`, `SMARTPAY_INVOICE_SERVICE_URL`, `SMARTPAY_NOTIFICATION_SERVICE_URL`, `SMARTPAY_RECON_SERVICE_URL` |
| **Ledger Service** | `smartpay-ledger-service` | `8081` | `9091` | `smartpay_db` (`ledger`) | Double-entry journal posting, balance transfers, hold/release lifecycle |
| **Payment Service** | `smartpay-payment-service` | `8082` | `9092` | `smartpay_db` (`payment`) | Faster Payments / VRP orchestration, Transactional Outbox (`SKIP LOCKED`) |
| **Invoice Service** | `smartpay-invoice-service` | `8083` | `9093` | `smartpay_db` (`invoice`) | ePOD signature verification, freight pricing (base + fuel + VAT) |
| **Payout Worker** | `smartpay-payout-worker` | `8084` | — | Stateless | Virtual Thread factoring payout background worker with Resilience4j |
| **Recon Service** | `smartpay-recon-service` | `8085` | `9094` | `smartpay_db` (`recon`) | CAMT.053 XML / MT940 bank statement reconciliation engine |
| **Risk Service** | `smartpay-risk-service` | `8086` | `9095` | `smartpay_db` (`risk`) | Carrier credit scoring, exposure limits, fraud propensity evaluation |
| **Notification Svc** | `smartpay-notification-service`| `8087` | — | `smartpay_db` (`notification`) | Event-driven Email / SMS / Webhook notification engine |
| **PostgreSQL 16** | `postgres` | `5432` | — | `smartpay_db` | Shared ACID database; each service owns an isolated PostgreSQL schema (`ledger`, `invoice`, `payment`, `recon`, …) with B-Tree UUIDv7 indexes |
| **Redpanda Broker** | `redpanda` | `9092` | — | — | Lightweight C++20 event streaming broker (Kafka wire-compatible) |
| **Redpanda Console**| `redpanda-console` | `8090` | — | — | Topic and message monitoring dashboard (`http://localhost:8090`) |

> **Public network access**: only `smartpay-gateway` (and ops tooling such as
> PostgreSQL/Redpanda in dev) is reachable externally. All business microservices
> are ClusterIP-only and communicate in-cluster via gRPC or Redpanda events;
> their HTTP ports above are internal service ports, never public.


---

## 🛠️ Local Environment & Quick Start

### 1. Prerequisites
* **Java 25 (Project Loom Virtual Threads)**:
  ```bash
  # Via SDKMAN:
  sdk install java 25-open
  # Verify:
  java -version # OpenJDK 25
  ```
* **Apache Maven 3.9+**:
  ```bash
  mvn -version
  ```
* **Docker & Docker Compose**:
  ```bash
  docker compose version
  ```

---

### 2. Start Infrastructure
Launch PostgreSQL 16, Redpanda (Kafka API), and Redpanda Console:
```bash
docker compose up -d
```
* **PostgreSQL**: `localhost:5432` (`smartpay_db`, user: `smartpay`, pass: `smartpay`)
* **Redpanda**: `localhost:9092`
* **Redpanda Console**: Open `http://localhost:8090`

> **Schema isolation**: All services share one PostgreSQL database (`smartpay_db`), but each microservice owns an isolated schema — `ledger`, `invoice`, `payment`, `recon`, `gateway`. On first startup every service's Flyway run creates its own schema automatically (`spring.flyway.create-schemas: true`) and applies its per-service migrations (each service's `db/migration` folder restarts at `V1`), so no schema provisioning is required beforehand.

---

### 3. Build Contracts & Project
Compile Protocol Buffers and build all microservice modules:
```bash
# Compile protobuf contracts first
mvn compile -pl smartpay-proto

# Build all modules
mvn compile
```

---

### 4. Running Test Suites

SmartPay strictly separates fast in-memory unit tests from containerized integration suites using JUnit 5 tags:

#### 🟢 Fast Unit Tests & Quality Verification (`mvn verify -Punit`)
Executes the full quality pipeline across all 11 modules in $< 50\text{ seconds}$ with **zero Docker/container footprint**:
* In-memory unit tests across all domain models, pricing math, MapStruct mappers, and calculation engines.
* **ArchUnit architecture fitness tests** across all microservices enforcing `.web` packaging, constructor injection, and Project Loom non-pinning.
* **Spotless code formatting & linting** check.
* **JaCoCo code coverage generation** producing HTML reports under `${module}/target/site/jacoco/index.html`.
* **Surefire test report generation** producing HTML reports under `${module}/target/reports/surefire.html`.
```bash
mvn verify -Punit

# Format codebase using Spotless:
mvn spotless:apply
```

#### 🔵 Full Integration Tests (`mvn test -Pintegration`)
Executes full-stack integration suites against Testcontainers PostgreSQL 16, WireMock HTTP endpoints, HTTP/2 gRPC channels on Virtual Threads, and Resilience4j circuit breakers:
```bash
mvn test -Pintegration
```

---

### 5. Ephemeral KinD Multi-Node Cluster & End-to-End Testing (TECH-003)

SmartPay provides automated one-command provisioning and verification for local Kubernetes testing:

#### ☸️ One-Command KinD Setup & E2E Runner (`scripts/ci/kind-setup.sh`)
Provisions a 3-node KinD cluster (1 control-plane, 2 workers with port mappings `80/443`), installs NGINX Ingress controller, deploys PostgreSQL 16 & Redpanda, applies Flyway migrations, seeds the demo ledger accounts, builds/loads distroless container images, rolls out microservices via Kustomize dev overlay, and executes the automated gateway-mediated E2E smoke suite:
```bash
chmod +x scripts/ci/kind-setup.sh
./scripts/ci/kind-setup.sh smartpay-cluster
```
The smoke suite (`e2e-smoke-test.sh`) and the full-lifecycle suite reach the platform **only through the gateway** (`GATEWAY_URL`, default `http://localhost:8080`); no business service port is called directly. Demo ledger accounts used by the suites are seeded by `scripts/ci/seed-demo-data.sh`.

#### 🧪 Full 10-Phase E2E Lifecycle Journey (`scripts/ci/e2e-full-lifecycle-test.sh`)
Executes all 10 stages of the end-to-end commercial freight payment lifecycle:
```bash
chmod +x scripts/ci/e2e-full-lifecycle-test.sh
./scripts/ci/e2e-full-lifecycle-test.sh
```

#### 🔍 Live Kafka / Redpanda Topic & Consumer Group Inspector (`scripts/ci/inspect-kafka-topics.sh`)
Inspects live event streams, consumer group offsets, and consumer lag directly on Redpanda:
```bash
chmod +x scripts/ci/inspect-kafka-topics.sh

# Summary of all topics and consumer groups:
./scripts/ci/inspect-kafka-topics.sh summary

# Consume recent settlement stream messages in JSON format:
./scripts/ci/inspect-kafka-topics.sh consume smartpay.events.payment 5

# Inspect consumer group lag (factoring workers & notification workers):
./scripts/ci/inspect-kafka-topics.sh lag

# Inspect Dead Letter Queue (DLQ):
./scripts/ci/inspect-kafka-topics.sh dlq
```

---

### 6. Running Microservices Locally
Services can be launched independently using the Spring Boot Maven plugin. In production-like
topologies every external request must go through the API Gateway — business services are
ClusterIP-only. For local runs, either call the gateway last and point it at the other services,
or invoke a service directly only for development:

```bash
# 1. Start Ledger Service (HTTP: 8081, gRPC: 9091)
mvn spring-boot:run -pl smartpay-ledger-service

# 2. Start Payment Service (HTTP: 8082, gRPC: 9092)
mvn spring-boot:run -pl smartpay-payment-service

# 3. Start Invoice & ePOD Service (HTTP: 8083)
mvn spring-boot:run -pl smartpay-invoice-service

# 4. Start Payout Factoring Worker (HTTP: 8084)
mvn spring-boot:run -pl smartpay-payout-worker

# 5. Start Bank Reconciliation Service (HTTP: 8085)
mvn spring-boot:run -pl smartpay-recon-service

# 6. Start Risk & Fraud Service (HTTP: 8086, gRPC: 9091)
mvn spring-boot:run -pl smartpay-risk-service

# 7. Start Notification Service (HTTP: 8087)
mvn spring-boot:run -pl smartpay-notification-service

# 8. Start API Gateway — the ONLY public entry point. Point its downstream
#    routes at the locally running services (defaults are localhost:8082/8083/8087/8085):
SMARTPAY_PAYMENT_SERVICE_URL=http://localhost:8082 \
SMARTPAY_INVOICE_SERVICE_URL=http://localhost:8083 \
SMARTPAY_NOTIFICATION_SERVICE_URL=http://localhost:8087 \
SMARTPAY_RECON_SERVICE_URL=http://localhost:8085 \
SMARTPAY_GATEWAY_SECURITY_ENABLED=false \
mvn spring-boot:run -pl smartpay-gateway
```
Route through the gateway for business calls, e.g. `POST http://localhost:8080/api/v1/payments/initiate`
with an `Idempotency-Key` and `X-Tenant-Id` header. The notification service additionally needs a
reachable `REDPANDA_BOOTSTRAP_SERVERS` broker (its dead-letter producer publishes there).
