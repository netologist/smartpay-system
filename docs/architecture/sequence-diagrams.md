# Sequence Diagrams

This document details the chronological interaction flow across microservices, databases, and external payment rails for core business workflows.

---

## 1. Electronic Proof of Delivery (ePOD) Verification & Invoice Issuance Flow

Traces the path from driver delivery completion to automated invoice generation:

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Carrier / Driver
    participant Gateway as API Gateway
    participant InvoiceSvc as smartpay-invoice-service
    participant S3 as AWS S3 Storage
    participant Kafka as Redpanda / Kafka
    participant PayoutWorker as smartpay-payout-worker

    Driver->>S3: Upload delivery proof photo
    S3-->>Driver: Return s3_photo_url

    Driver->>Gateway: POST /api/v1/epod/verify (loadId, coords, signatureHash, s3_url)
    Gateway->>InvoiceSvc: Forward request
    
    Note over InvoiceSvc: Verify GPS boundaries &<br/>validate SHA-256 signature hash
    InvoiceSvc->>InvoiceSvc: Insert epod_records (verified=true)
    
    Note over InvoiceSvc: Run Pricing Engine:<br/>Base + 12% Fuel + 20% VAT = Total
    InvoiceSvc->>InvoiceSvc: Insert invoices (status=EPOD_VERIFIED)

    InvoiceSvc->>Kafka: Publish event: smartpay.events.invoice (InvoiceIssuedEvent)
    InvoiceSvc-->>Gateway: HTTP 201 Created (InvoicePricing DTO)
    Gateway-->>Driver: Invoice issued successfully

    Kafka->>PayoutWorker: InvoiceIssuedEvent consumed
    Note over PayoutWorker: Factoring eligibility evaluation triggered
```

---

## 2. Instant Factoring Payout & Double-Entry Ledger Posting Flow

Traces invoice advance disbursement, balance hold reservation, and two-tier ledger settlement:

```mermaid
sequenceDiagram
    autonumber
    participant Kafka as Redpanda / Kafka
    participant Worker as smartpay-payout-worker (K8s Consumer)
    participant RiskSvc as smartpay-risk-service
    participant PaymentSvc as smartpay-payment-service
    participant LedgerSvc as smartpay-ledger-service
    participant Bank as ClearBank API

    Kafka->>Worker: Consume EpodVerifiedEvent (loadId, carrierId, £1000)
    Note over Worker: Consumer Group Partitioning:<br/>Exactly one pod assigned per message

    Note over Worker: Compute factoring advance:<br/>Total £1000 - 2.5% Fee (£25) = Net £975
    Worker->>RiskSvc: gRPC: EvaluateCarrierRisk(CarrierId, £1000)
    RiskSvc-->>Worker: RiskResponse (approved=true, score=15)

    Worker->>PaymentSvc: gRPC: InitiatePayment (Escrow, Carrier, £975, IdempotencyKey)

    Note over PaymentSvc: Insert idempotency_records<br/>status = PROCESSING
    PaymentSvc->>LedgerSvc: gRPC: HoldFunds (EscrowAccount, £975, EndToEndId)
    
    Note over LedgerSvc: Acquire SELECT FOR UPDATE lock<br/>Verify available_balance >= £975<br/>Increment hold_balance_pence
    LedgerSvc-->>PaymentSvc: HoldFundsResponse (new_hold_balance)

    PaymentSvc->>PaymentSvc: Insert transactional_outbox (PAYMENT_INITIATED)
    PaymentSvc-->>Worker: InitiatePaymentResponse (status=INITIATED)
    Worker->>Kafka: Publish FactoringPayoutApprovedEvent (smartpay.events.factoring)

    Note over PaymentSvc: Outbox Worker polls event (SKIP LOCKED)
    PaymentSvc->>Bank: Faster Payments API call (£975)
    Bank-->>PaymentSvc: 200 OK (Settled / Transfer Sent)

    PaymentSvc->>LedgerSvc: gRPC: ReleaseHold (capture=true, £975)
    Note over LedgerSvc: Post Zero-Sum Journal Lines:<br/>DEBIT Escrow £975 | CREDIT Carrier £975<br/>Decrement cleared_balance permanently
    LedgerSvc-->>PaymentSvc: ReleaseHoldResponse (captured=true)

    PaymentSvc->>Kafka: Publish event: PaymentSettledEvent
```
---

## 3. Bank Statement (CAMT.053) Ingestion & Auto-Reconciliation Flow

Traces bank statement line parsing and matching against ledger journal transactions:

```mermaid
sequenceDiagram
    autonumber
    actor Ops as Finance Operations Admin
    participant Gateway as API Gateway
    participant ReconSvc as smartpay-recon-service
    participant LedgerSvc as smartpay-ledger-service

    Ops->>Gateway: POST /api/v1/recon/statements/upload (CAMT.053 XML file)
    Gateway->>ReconSvc: Forward upload

    Note over ReconSvc: XML Parser executes:<br/>Read statement header and lines
    ReconSvc->>ReconSvc: Persist bank_statements & lines (status=UNMATCHED)

    loop For each statement line
        ReconSvc->>LedgerSvc: gRPC: GetTransactionByReference (end_to_end_id)
        alt Matching journal transaction found and amount/currency match
            LedgerSvc-->>ReconSvc: TransactionDetail (amount, currency, status=POSTED)
            ReconSvc->>ReconSvc: Update line (status=MATCHED, matched_entry_id)
        else Amount discrepancy or transaction not found
            ReconSvc->>ReconSvc: Flag line (status=DISCREPANCY)
            Note over ReconSvc: Generate audit alert
        end
    end

    ReconSvc-->>Gateway: Reconciliation Report (e.g. 98 Matched, 2 Discrepancies)
    Gateway-->>Ops: Report displayed in dashboard
```

---

## 4. Event-Driven Multi-Channel Notification & Delivery Confirmation Flow

Traces the asynchronous event-driven customer alerting lifecycle across Java 25 Virtual Threads, consumer deduplication, Resilience4j retries, and Dead Letter Queue (DLQ) poison-pill isolation:

```mermaid
sequenceDiagram
    autonumber
    participant Kafka as Redpanda / Kafka (smartpay.events.payment)
    participant Listener as NotificationEventListener (Virtual Thread)
    participant Idemp as NotificationIdempotencyService
    participant Renderer as TemplateEngine
    participant Dispatcher as NotificationDispatchService
    participant Provider as Twilio SMS / SendGrid Email API
    participant DB as PostgreSQL (notification.notification_logs)
    participant DLQ as Kafka DLQ (smartpay.events.notifications.dlq)

    Kafka->>Listener: Consume PaymentSettledEvent (eventId, settledAmount=£975.00)
    Note over Listener: Offload to Java 25 Virtual Thread<br/>(Non-blocking Project Loom execution)

    Listener->>Idemp: isEventProcessed(eventId, SMS)
    Idemp->>DB: SELECT status FROM notification_logs WHERE event_id = :id AND channel = 'SMS'
    DB-->>Idemp: Status result

    alt Already Processed (Idempotency Hit - AC-2)
        Idemp-->>Listener: true (Duplicate detected)
        Listener->>Kafka: Commit offset (Acknowledge and suppress duplicate SMS)
    else First-Time Processing (AC-1)
        Idemp-->>Listener: false
        Listener->>Renderer: render("PAYMENT_SETTLED", SMS, params)
        Renderer-->>Listener: RenderedMessage ("SmartPay: Payout £975.00 settled to FastFreight")

        Listener->>Dispatcher: dispatch(eventId, SMS, phone, message)
        Dispatcher->>DB: INSERT INTO notification_logs (status=PENDING)

        Note over Dispatcher: Wrapped in Resilience4j @Retry (maxAttempts=3, exponential backoff)
        Dispatcher->>Provider: HTTP POST /Messages.json (Twilio REST)

        alt Provider 2xx Success (HTTP 201 Created)
            Provider-->>Dispatcher: ProviderReceipt (providerMessageId="SM-9912", status=DISPATCHED)
            Dispatcher->>DB: UPDATE notification_logs SET status='DISPATCHED', provider_message_id='SM-9912'
            Dispatcher-->>Listener: NotificationDispatchResult(DISPATCHED)
            Listener->>Kafka: Commit offset (ACK)
        else Provider Outage / 429 Rate Limit (Retries Exhausted - AC-3)
            Provider-->>Dispatcher: HTTP 500 Internal Server Error (x3 attempts)
            Dispatcher->>DB: UPDATE notification_logs SET status='DEAD_LETTERED', error_message='Twilio 500'
            Dispatcher->>DLQ: Publish DeadLetterNotificationPayload (smartpay.events.notifications.dlq)
            Dispatcher-->>Listener: NotificationDispatchResult(DEAD_LETTERED)
            Listener->>Kafka: Commit offset (ACK - avoid poison pill consumer loop)
        end
    end
```
