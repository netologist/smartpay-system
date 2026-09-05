# ADR-006: Two-Tier Distributed Idempotency via SHA-256 Fingerprinting

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
In financial APIs, clients routinely retry operations due to network drops, timeouts, or mobile disconnection. Without robust idempotency protection:
* A carrier might be disbursed funds twice for the same freight invoice.
* Malicious actors could manipulate request bodies under a reused idempotency key to alter payment destinations or amounts.

## Decision
Enforce a **Two-Tier Distributed Idempotency State Machine** on all state-mutating endpoints:
1. **Tier 1: Request Fingerprinting**: Calculate a cryptographic SHA-256 hash of the incoming HTTP payload (`request_hash`).
2. **Tier 2: Database Composite Lock (`tenant_id`, `idempotency_key`)**:
   Insert a row into `idempotency_records` with `status = 'PROCESSING'`.
   * If a record already exists with status `PROCESSING`: Reject with `IdempotencyConflictException` (HTTP 409).
   * If a record exists but the incoming `request_hash` differs: Reject with `RequestHashMismatchException` (HTTP 422 - tamper attempt).
   * If a record exists with status `COMPLETED`: Short-circuit business logic and return the cached `response_code` and `response_body` immediately with header `X-Cache: IDEMPOTENT-HIT`.
3. **TTL Clean-up**: Idempotency records expire after 24 hours via `expires_at` and an automated purging job.

## Consequences
* **Positive**: Absolute prevention of duplicate payouts, tamper-resistant request validation, sub-millisecond cached responses on client retries.
* **Negative**: Introduces an extra database read/write per mutating request (an essential trade-off in financial architectures).
