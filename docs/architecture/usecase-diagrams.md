# Use Case Diagrams

This document visualizes the primary interactions between external actors and the SmartPay Logistics Payment Platform.

---

## 1. Primary Actors

* 🏢 **Shipper (Freight Customer)**: Creates freight transport orders, reviews itemized invoices, deposits funds into escrow, and approves final settlements.
* 🚚 **Carrier / Driver**: Transports loads, uploads Electronic Proof of Delivery (ePOD) with GPS coordinates and cryptographic signatures, and requests instant factoring liquidity.
* 👔 **Finance Operations Administrator**: Monitors double-entry ledger health, uploads ISO-20022 bank statements, investigates discrepancies, and reconciles statements.
* 🤖 **System Background Workers**: Automated workers handling transactional outbox event publishing, factoring eligibility polling, and scheduled reconciliation.
* 🏦 **Banking & Payment Rails**: External clearing systems including Faster Payments, Open Banking Variable Recurring Payments (VRP), and ClearBank/Barclays partner APIs.

---

## 2. Platform Use Case Map

```mermaid
graph LR
    subgraph Actors[Actors]
        Shipper((Shipper))
        Carrier((Carrier))
        FinOps((Finance Ops))
        BankWorker((Background Worker))
    end

    subgraph InvoicingContext[Invoicing & ePOD Context]
        UC_UploadEpod[Upload Delivery Proof ePOD]
        UC_CalculateInvoice[Calculate Freight Pricing]
        UC_ApproveInvoice[Approve Freight Invoice]
    end

    subgraph LedgerContext[General Ledger Context]
        UC_DoubleEntry[Post Zero-Sum Journal Entry]
        UC_HoldFunds[Reserve Balance Hold]
        UC_ReleaseHold[Release / Capture Hold]
        UC_AuditLedger[Audit Accounting Trail]
    end

    subgraph PaymentContext[Payment & Factoring Context]
        UC_RequestFactoring[Request Factoring Liquidity]
        UC_ExecutePayout[Disburse Carrier Payout]
        UC_InitiateVrp[Initiate VRP / Faster Payment]
    end
    subgraph ReconContext[Reconciliation Context]
        UC_UploadCamt[Upload CAMT.053 Statement XML]
        UC_AutoMatch[Match Lines via EndToEndId]
        UC_ResolveDiscrepancy[Resolve Statement Discrepancy]
    end

    subgraph RiskContext[Risk & Fraud Context]
        UC_ScoreCarrier[Evaluate Carrier Credit Risk]
        UC_CheckExposure[Verify Factoring Exposure Cap]
        UC_DetectCollusion[Screen Anti-Collusion Heuristics]
    end

    subgraph NotificationContext[Notification Context]
        UC_SendSmsAlert[Dispatch Settlement SMS]
        UC_SendInvoiceEmail[Send Invoice Ready Email]
        UC_DispatchWebhook[Emit Signed Partner Webhook]
    end

    Carrier --> UC_UploadEpod
    Carrier --> UC_RequestFactoring

    Shipper --> UC_ApproveInvoice
    Shipper --> UC_InitiateVrp

    FinOps --> UC_AuditLedger
    FinOps --> UC_UploadCamt
    FinOps --> UC_ResolveDiscrepancy

    BankWorker --> UC_CalculateInvoice
    BankWorker --> UC_DoubleEntry
    BankWorker --> UC_ExecutePayout
    BankWorker --> UC_AutoMatch
    BankWorker --> UC_ScoreCarrier
    BankWorker --> UC_CheckExposure
    BankWorker --> UC_SendSmsAlert
    BankWorker --> UC_SendInvoiceEmail
    BankWorker --> UC_DispatchWebhook

---

## 3. Detailed Use Case Specifications

### A) Carrier Scenarios
1. **Upload Delivery Proof (ePOD)**:
   * Driver captures delivery photo, collects recipient signature, and submits `latitude`, `longitude`, `signature_hash`, and S3 photo link via mobile client.
2. **Request Instant Factoring Liquidity**:
   * Upon delivery verification, carrier bypasses standard 30-90 day net payment terms and requests instant disbursement with a 2.5% platform fee deduction.

### B) Shipper Scenarios
1. **Approve Freight Invoice**:
   * Shipper reviews the itemized invoice (base rate, fuel surcharge, VAT) calculated from mileage and vehicle type, authorizing payment from escrow.
2. **Initiate Open Banking VRP**:
   * Shipper funds platform accounts or pays freight bills directly via Variable Recurring Payments (VRP) to avoid merchant card interchange fees.

### C) Finance Operations Scenarios
1. **Upload Bank Statement (CAMT.053 / MT940)**:
   * Administrator uploads daily bank statement XML files retrieved from clearing banks.
2. **Resolve Reconciliation Discrepancies**:
   * Lines failing automated matching (due to fee deductions or mismatched references) are inspected and cleared with manual adjustment entries.

### D) Background Worker Scenarios
1. **Transactional Outbox Publisher**:
   * Continuously polls `transactional_outbox` rows with `SKIP LOCKED` and streams events into Redpanda/Kafka topics with at-least-once delivery guarantees.
2. **Factoring Payout Worker**:
   * Consumes `EpodVerifiedEvent` from Kafka, verifies fraud scores and exposure limits with Risk Service via gRPC, deducts 2.5% fee, and triggers immediate payment via Payment gRPC API.

### E) Risk & Fraud Assessment Scenarios
1. **Evaluate Carrier Credit Risk**:
   * Analyzes historical delivery completion, factoring repayment rate, and credit score (0-100) before authorizing liquidity advance.
2. **Check Factoring Exposure Cap**:
   * Ensures a carrier's cumulative outstanding advance does not exceed their £50,000 daily credit limit.
3. **Screen Anti-Collusion Heuristics**:
   * Checks for shared IP subnets, bank accounts, or corporate directors between shipper and carrier to block fraudulent collusion rings.

### F) Multi-Channel Notification Scenarios
1. **Dispatch Settlement SMS**:
   * Sends immediate SMS confirmation via Twilio to carrier's mobile phone as soon as Faster Payments clears.
2. **Send Invoice Ready Email**:
   * Sends PDF invoice with itemized pricing (base, fuel surcharge, VAT) to shipper via SendGrid upon delivery verification.
3. **Emit Signed Partner Webhooks**:
   * Dispatches signed HMAC-SHA256 webhook payloads to enterprise TMS (Transport Management System) integration endpoints.
