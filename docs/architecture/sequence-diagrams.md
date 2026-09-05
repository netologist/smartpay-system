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
    participant Worker as smartpay-payout-worker
    participant PaymentSvc as smartpay-payment-service
    participant LedgerSvc as smartpay-ledger-service
    participant Bank as ClearBank API
    participant Kafka as Redpanda / Kafka

    Note over Worker: Compute factoring advance:<br/>Total £1000 - 2.5% Fee (£25) = Net £975
    Worker->>PaymentSvc: gRPC: InitiatePayment (CarrierId, £975, EndToEndId)

    Note over PaymentSvc: Insert idempotency_records<br/>status = PROCESSING
    PaymentSvc->>LedgerSvc: gRPC: HoldFunds (EscrowAccount, £975, EndToEndId)
    
    Note over LedgerSvc: Acquire SELECT FOR UPDATE lock<br/>Verify available_balance >= £975<br/>Increment hold_balance_pence
    LedgerSvc-->>PaymentSvc: HoldFundsResponse (new_hold_balance)

    PaymentSvc->>PaymentSvc: Insert transactional_outbox (PAYMENT_INITIATED)
    PaymentSvc-->>Worker: InitiatePaymentResponse (status=PROCESSING)

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
