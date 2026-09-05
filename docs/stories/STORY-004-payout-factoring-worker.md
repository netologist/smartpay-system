# STORY-004: Carrier Factoring & Instant Payout Worker

## 📌 Overview
* **Target Module**: `smartpay-payout-worker`
* **Priority**: P1 (Factoring Liquidity Engine)
* **Associated Services**: `smartpay-invoice-service`, `smartpay-payment-service`
* **Required `smartpay-common` Components**:
  * `Money` (Gross invoice, factoring fee, and net payout calculation)
  * `CarrierId`, `InvoiceId`, `TransactionId` (Strongly-typed IDs)
  * `FactoringPayoutApprovedEvent`
  * Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`)

---

## 🎯 User Story
> **As an** Automated Factoring Liquidity Worker,  
> **I want to** poll delivered freight invoices, evaluate credit and fraud scores, and disburse carrier funds immediately with a 2.5% platform fee deduction,  
> **So that** carriers achieve instant cash flow liquidity rather than waiting 30-90 days, while the platform captures factoring fee revenue.

---

## 📐 Architecture & Pricing Rules

1. **Factoring Fee Calculation Invariant**:
   $$\text{Factoring Fee} = \text{Invoice Total} \times 0.025 \quad (2.5\%)$$
   $$\text{Payout Amount} = \text{Invoice Total} - \text{Factoring Fee}$$
   Example (£1000 Invoice):
   * Factoring Fee (Platform Revenue): $£1000 \times 0.025 = £25.00$
   * Net Carrier Payout: $£1000 - £25 = £975.00$

2. **Virtual Thread Worker Pool**:
   * Each invoice payout must execute inside an independent virtual thread (`Thread.ofVirtual()`).
   * Blocking gRPC or HTTP RPC calls yield carrier threads without consuming OS thread pool capacity.

---

## ✅ Acceptance Criteria (AC)

### AC-1: Polling Verified Invoices
* **Given**: Eligible invoices in `smartpay-invoice-service` with `status = EPOD_VERIFIED`,
* **When**: The factoring scheduler executes,
* **Then**: Invoices are fetched and net factoring amounts are computed accurately.

### AC-2: Payout Execution via Payment gRPC
* **Given**: An invoice with £1000 total and £975 net payout,
* **When**: `executeInstantPayout(invoiceId)` executes,
* **Then**: `PaymentService` gRPC is called to disburse funds to the carrier, and the invoice transitions to `FACTORING_APPROVED`.

---

## 💻 Class Implementation Structure

```
smartpay-payout-worker/src/main/java/com/hozgan/smartpay/payout/
├── service/
│   ├── FactoringCalculationService.java  // 2.5% fee and net calculation
│   └── PayoutExecutionService.java       // Payment gRPC integration
├── worker/
│   └── FactoringPayoutScheduler.java     // Virtual Thread scheduled poller
└── config/
    └── VirtualThreadExecutorConfig.java  // Project Loom Executor configuration
```
