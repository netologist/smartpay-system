# STORY-006: API Gateway & Distributed Idempotency Filter

## 📌 Overview
* **Target Module**: `smartpay-gateway`
* **Priority**: P2 (Ingress Security & Edge Filtering)
* **Associated Database Tables**: `idempotency_records` (`V5`)
* **Required `smartpay-common` Components**:
  * `TenantId`, `IdempotencyKey` (Strongly-typed IDs)
  * `IdempotencyStatus`
  * `IdempotencyConflictException`, `RequestHashMismatchException`

---

## 🎯 User Story
> **As the** API Gateway,  
> **I want to** inspect incoming financial HTTP requests (POST/PUT) for mandatory `Idempotency-Key` headers and compute SHA-256 body fingerprints,  
> **So that** network retries and duplicate submissions are intercepted at the perimeter before triggering backend service workloads.

---

## 📐 Architecture & Filter Invariants

1. **Header Enforcement**:
   * Financial mutation endpoints (`/api/v1/payments/**`, `/api/v1/invoices/**`) mandate the `Idempotency-Key` HTTP header.
   * Requests lacking this header are rejected immediately with HTTP 400 Bad Request.

2. **Request Fingerprinting**:
   * The JSON body is hashed with SHA-256 (`request_hash`).
   * A request bearing a reused key but a mutated body is rejected with `RequestHashMismatchException` (HTTP 422).

3. **Perimeter Response Caching**:
   * Upstream HTTP status codes and responses are cached in `idempotency_records`. Replayed requests receive the cached response directly with header `X-Cache: IDEMPOTENT-HIT`.

---

## ✅ Acceptance Criteria (AC)

### AC-1: Missing Header Rejection
* **Given**: A payment request submitted without an `Idempotency-Key` header,
* **When**: The gateway filter processes the request,
* **Then**: The request is aborted before reaching downstream services, returning HTTP 400 Bad Request.

### AC-2: Response Caching on First Success
* **Given**: A novel, valid payment request,
* **When**: The downstream service responds with HTTP 201 Created,
* **Then**: The gateway caches the status code and response payload in `idempotency_records` (`status = COMPLETED`).

### AC-3: Instant Perimeter Response on Retries
* **Given**: An incoming request matching a previously completed key and body hash,
* **When**: The gateway filter executes,
* **Then**: Downstream services are bypassed completely, and the cached response is served with header `X-Cache: IDEMPOTENT-HIT`.

---

## 💻 Class Implementation Structure

```
smartpay-gateway/src/main/java/com/hozgan/smartpay/gateway/
├── filter/
│   ├── IdempotencyGatewayFilter.java   // Servlet / WebFilter filter implementation
│   └── RequestCachingWrapper.java      // Multi-read cached HTTP request wrapper
└── service/
    └── GatewayIdempotencyService.java  // Database lock and cache persistence service
```
