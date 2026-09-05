# STORY-002: Freight Invoicing & ePOD Pricing Engine

## 📌 Overview
* **Target Module**: `smartpay-invoice-service`
* **Priority**: P1 (Logistics Billing & Delivery Verification)
* **Associated Database Tables**: `epod_records`, `invoices` (`V3`)
* **Required `smartpay-common` Components**:
  * `Money` (Base freight, fuel surcharge, VAT, and total calculations)
  * `InvoicePricing` (Validation invariant: base + fuel + vat == total)
  * `LoadId`, `CarrierId`, `ShipperId`, `InvoiceId` (Strongly-typed IDs)
  * `GeoLocation` (Latitude/Longitude bounds validation)
  * `SignatureHash` (64-character SHA-256 validation)
  * `VehicleType` (`VAN`, `LUTON`, `7_5T`, `ARTIC`), `InvoiceStatus`
  * `InvalidEpodSignatureException`, `DuplicateLoadException`, `InvoiceAlreadySettledException`
  * `EpodVerifiedEvent`, `InvoiceIssuedEvent`

---

## 🎯 User Story
> **As a** Freight Billing Engine,  
> **I want to** cryptographically and geographically verify driver delivery proofs (ePOD), and automatically calculate freight invoices based on mileage and vehicle type,  
> **So that** invoices are generated immediately upon delivery with zero human error, enabling instant factoring liquidity.

---

## 📐 Architecture & Pricing Rules

1. **Itemized Pricing Invariant**:
   Total freight invoice pricing must strictly satisfy:
   $$\text{Total} = \text{Base Amount} + \text{Fuel Surcharge} + \text{VAT}$$
   * **Base Rate per Mile by Vehicle Type**:
     * `VAN`: £1.50 / mile
     * `LUTON`: £2.00 / mile
     * `7_5T`: £2.75 / mile
     * `ARTIC`: £3.50 / mile
   * **Fuel Surcharge**: Exactly 12% of the Base Amount (`baseAmount.percent(12)`).
   * **VAT (UK Standard Rate)**: Exactly 20% on the subtotal (`(base + fuel).percent(20)`).
   * Invariant is guaranteed by `InvoicePricing.java`.

2. **Delivery Proof (ePOD) Invariant**:
   * S3 photo URL must be present and valid.
   * `SignatureHash` must be a valid 64-character hexadecimal SHA-256 digest.
   * Submissions for an already verified `load_id` must throw `DuplicateLoadException`.

---

## ✅ Acceptance Criteria (AC)

### AC-1: Electronic Delivery Proof (ePOD) Verification
* **Given**: A driver submits an ePOD with GPS coordinates (51.5074, -0.1278), a valid SHA-256 signature hash, and an S3 photo link,
* **When**: `verifyEpod(command)` is invoked,
* **Then**: The record is stored in `epod_records` with `verified = true`, and an `EpodVerifiedEvent` is published to Kafka.
* **And**: If the signature hash is malformed, `InvalidEpodSignatureException` is thrown.

### AC-2: Automated Freight Pricing & Invoice Issuance
* **Given**: A verified freight load with 100 miles completed via a `LUTON` van,
* **When**: `generateInvoice(loadId, shipperId, carrierId, vehicleType, mileage, currency)` is called:
  * Base Amount: $100 \times £2.00 = £200.00$
  * Fuel Surcharge: $£200.00 \times 0.12 = £24.00$
  * VAT (20%): $(£200.00 + £24.00) \times 0.20 = £44.80$
  * Total Invoice: $£200.00 + £24.00 + £44.80 = £268.80$
* **Then**: An `invoices` row is persisted with `status = EPOD_VERIFIED`, and an `InvoiceIssuedEvent` is published to Kafka.

### AC-3: Invoice State Transition Safety
* **Given**: An invoice with status `SETTLED`,
* **When**: A cancellation or modification request is received,
* **Then**: The request is rejected by throwing `InvoiceAlreadySettledException`.

---

## 💻 Class Implementation Structure

```
smartpay-invoice-service/src/main/java/com/hozgan/smartpay/invoice/
├── service/
│   ├── EpodService.java                // ePOD verification and ingest
│   ├── FreightPricingCalculator.java   // Vehicle rate and surcharge calculation
│   ├── InvoiceService.java             // Invoice lifecycle and state machine
│   └── impl/
│       ├── EpodServiceImpl.java
│       ├── FreightPricingCalculatorImpl.java
│       └── InvoiceServiceImpl.java
├── web/
│   ├── EpodController.java             // POST /api/v1/epod/verify
│   └── InvoiceController.java          // GET/POST /api/v1/invoices
└── dto/
    ├── VerifyEpodRequest.java
    └── CreateInvoiceRequest.java
```
