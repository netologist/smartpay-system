# C4 Architecture Models

This document presents the layered architecture diagrams for the SmartPay Logistics Payment Platform following the **C4 Model** (Context, Container, Component, Code) standard.

---

## 🏛️ Level 1: System Context Diagram

Illustrates the SmartPay platform within its operating environment, detailing human actors and external banking/logistics systems.

```mermaid
C4Context
    title System Context Diagram - SmartPay Platform

    Person(shipper, "Shipper (Merchant)", "Corporate freight customer ordering transport and settling invoices.")
    Person(carrier, "Carrier / Driver", "Logistics actor delivering freight, uploading ePOD proof, and requesting factoring liquidity.")
    Person(financeOps, "Finance Operations Admin", "Internal administrator overseeing bank statement reconciliation and financial ledger audits.")

    Enterprise_Boundary(b0, "SmartPay Logistics Payment Platform") {
        System(smartpay, "SmartPay Core Platform", "Distributed platform managing double-entry ledger, freight invoicing, factoring liquidity, and bank reconciliation.")
    }

    System_Ext(bankRails, "Banking & Clearing Rails", "ClearBank, Barclays, Modulr (Faster Payments, BACS, Open Banking VRP APIs).")
    System_Ext(s3Storage, "AWS S3 / MinIO Storage", "Object storage for ePOD delivery photos and cryptographic digital signature proofs.")
    System_Ext(notificationProvider, "SMS / Email Dispatcher", "Twilio / SendGrid communication gateways for delivery and settlement alerts.")

    Rel(shipper, smartpay, "Views invoices, issues payments", "HTTPS / REST")
    Rel(carrier, smartpay, "Submits ePOD proofs, requests instant factoring", "HTTPS / Mobile App")
    Rel(financeOps, smartpay, "Uploads statements, monitors reconciliation reports", "HTTPS / Web UI")

    Rel(smartpay, bankRails, "Executes payments, pulls CAMT.053 statements", "mTLS / REST / ISO-20022")
    Rel(smartpay, s3Storage, "Archives delivery proof photos", "S3 API / IAM")
    Rel(smartpay, notificationProvider, "Dispatches payment and invoice alerts", "REST / Webhooks")
```

---

## 📦 Level 2: Container Diagram

Depicts the microservices, data stores, and communication protocols (gRPC, Kafka, REST) that constitute the SmartPay platform.

```mermaid
C4Container
    title Container Diagram - SmartPay Platform

    Person(client, "Clients", "Web UI / Mobile Apps")

    Container(gateway, "API Gateway", "Spring Boot 4.1 / Java 25", "Reverse proxy, JWT validation, rate limiting, and two-tier SHA-256 idempotency filter.")

    Container_Boundary(microservices, "Microservice Ecosystem") {
        Container(invoiceService, "Invoice Service", "Spring Boot / Java 25", "ePOD signature verification, freight pricing engine (base + fuel + VAT), invoice lifecycle.")
        Container(ledgerService, "Ledger Service", "Spring Boot / Java 25", "Double-entry general ledger, zero-sum invariant, balance hold/release, atomic transfer engine.")
        Container(paymentService, "Payment Service", "Spring Boot / Java 25", "Payment initiation, Faster Payments/VRP orchestration, Transactional Outbox.")
        Container(payoutWorker, "Payout Worker", "Spring Boot / Java 25", "Event-driven factoring worker consuming EpodVerifiedEvent via Kafka Consumer Groups and executing instant disbursements via Virtual Threads.")
        Container(reconService, "Reconciliation Service", "Spring Boot / Java 25", "CAMT.053 XML / MT940 bank statement ingestion and end_to_end_id ledger reconciliation.")
        Container(riskService, "Risk Service", "Spring Boot / Java 25", "Carrier and shipper credit risk scoring and fraud prevention.")
        Container(notificationService, "Notification Service", "Spring Boot / Java 25", "Event-driven SMS / Email notification engine.")
    }

    ContainerDb(ledgerDb, "Ledger DB", "PostgreSQL 16", "accounts, account_balances, journal_transactions, journal_entries")
    ContainerDb(invoiceDb, "Invoice DB", "PostgreSQL 16", "epod_records, invoices")
    ContainerDb(paymentDb, "Payment DB", "PostgreSQL 16", "transactional_outbox, idempotency_records")
    ContainerDb(reconDb, "Recon DB", "PostgreSQL 16", "bank_statements, bank_statement_lines")
    ContainerQueue(kafka, "Redpanda / Kafka", "Kafka Wire Protocol", "smartpay.events.* (epod-verified, invoice-issued, payment-settled, ledger-posted)")

    Rel(client, gateway, "API Requests", "HTTPS / JSON")
    Rel(gateway, invoiceService, "Invoice & ePOD calls", "HTTP / REST")
    Rel(gateway, paymentService, "Payment orders", "HTTP / REST")

    Rel(invoiceService, kafka, "Publishes EpodVerifiedEvent, InvoiceIssuedEvent", "Kafka Producer")
    Rel(kafka, payoutWorker, "Consumes EpodVerifiedEvent (partitioned group)", "Kafka Consumer Group")
    Rel(payoutWorker, riskService, "Evaluates carrier credit risk", "gRPC over HTTP/2")
    Rel(payoutWorker, paymentService, "Initiates factoring payout", "gRPC over HTTP/2")
    Rel(paymentService, ledgerService, "HoldFunds, TransferFunds", "gRPC over HTTP/2 (smartpay-proto)")
    Rel(paymentService, kafka, "Publishes events via Outbox Worker", "Kafka Producer")

    Rel(reconService, ledgerService, "Verifies transaction references", "gRPC over HTTP/2")

    Rel(ledgerService, ledgerDb, "Read/write data (Pessimistic Lock)", "JDBC / HikariCP")
    Rel(invoiceService, invoiceDb, "Invoices and ePOD records", "JDBC / HikariCP")
    Rel(paymentService, paymentDb, "Outbox and Idempotency records", "JDBC / HikariCP")
    Rel(reconService, reconDb, "Statement lines and matching", "JDBC / HikariCP")
```

---

## 🧩 Level 3: Component Diagram (Ledger Service)

Breaks down the internal architecture of `smartpay-ledger-service`, Platform's core double-entry accounting engine.

```mermaid
C4Component
    title Component Diagram - smartpay-ledger-service

    Container_Boundary(ledgerBoundary, "smartpay-ledger-service") {
        Component(ledgerGrpc, "LedgerGrpcService", "gRPC Controller", "Implementation of smartpay-proto LedgerServiceImplBase. Endpoints: GetBalance, TransferFunds, HoldFunds.")
        Component(protoMapper, "LedgerProtoMapper", "Mapper", "Bidirectional mapper between Protobuf MoneyProto and smartpay-common Money.")
        Component(balanceService, "AccountBalanceService", "Domain Service", "Pessimistic locking balance deduction, hold/release management, negative balance protection.")
        Component(ledgerEngine, "LedgerDomainService", "Domain Service", "Double-entry journal validation (SUM(DEBIT) == SUM(CREDIT)) and append-only persistence.")
        Component(balanceRepo, "AccountBalanceRepository", "Spring Data JPA", "Data access with PESSIMISTIC_WRITE locking and optimistic version tracking.")
        Component(accountRepo, "AccountRepository", "Spring Data JPA", "Chart of accounts persistence.")
        Component(txRepo, "JournalTransactionRepository", "Spring Data JPA", "Transaction headers and idempotency checking.")
        Component(entryRepo, "JournalEntryRepository", "Spring Data JPA", "Immutable debit/credit ledger lines.")
    }

    Rel(ledgerGrpc, protoMapper, "DTO / Proto transformation", "Java in-process")
    Rel(ledgerGrpc, balanceService, "Triggers balance operations", "Java in-process")
    Rel(ledgerGrpc, ledgerEngine, "Triggers journal posting", "Java in-process")

    Rel(balanceService, balanceRepo, "SELECT ... FOR UPDATE", "JPA")
    Rel(balanceService, ledgerEngine, "Posts journal lines for transfer", "Java in-process")

    Rel(ledgerEngine, txRepo, "Writes transaction header", "JPA")
    Rel(ledgerEngine, entryRepo, "Appends debit/credit lines", "JPA")
```

---

## 💻 Level 4: Code Diagram (Core Domain Model)

Demonstrates the object-oriented and record-based domain model implemented in `smartpay-common`.

```mermaid
classDiagram
    direction TB

    class EntityId~T~ {
        <<interface>>
        +value() T
        +asString() String
    }

    class AccountId {
        <<record>>
        -UUID value
        +generate() AccountId$
        +of(UUID) AccountId$
        +of(String) AccountId$
    }

    class Money {
        <<record>>
        -BigDecimal amount
        -Currency currency
        +plus(Money) Money
        +minus(Money) Money
        +times(BigDecimal) Money
        +divide(BigDecimal) Money
        +percent(BigDecimal) Money
        +toMinorUnits() long
        +atLeast(Money) boolean
        +atMost(Money) boolean
        +isZero() boolean
        +isPositive() boolean
    }

    class AccountBalance {
        <<record>>
        -AccountId accountId
        -Money clearedBalance
        -Money holdBalance
        -long version
        +availableBalance() Money
        +canCover(Money) boolean
    }

    class SmartpayDomainException {
        <<abstract sealed>>
        -String errorCode
        -Instant timestamp
        +errorCode() String
        +timestamp() Instant
    }

    class InsufficientFundsException {
        <<final>>
        -AccountId accountId
        -Money requestedAmount
        -Money availableBalance
    }

    class UnbalancedJournalTransactionException {
        <<final>>
        -Money totalDebit
        -Money totalCredit
    }

    EntityId <|.. AccountId : implements
    AccountBalance --> AccountId : belongs to
    AccountBalance --> Money : cleared & hold
    SmartpayDomainException <|-- InsufficientFundsException : permits
    SmartpayDomainException <|-- UnbalancedJournalTransactionException : permits
```
