# Technical Debt Record: TD-003

## Title
**Wire / DTO Money Representation: Pence `long` → Real-World `amount` + `currency` Object**

## Status
**Recorded / Deferred** (Priority: High — API contract alignment)

## Date
2026-09-08

---

## 📌 Problem Statement & Context

Request/response DTOs on the HTTP boundary currently carry monetary values as **minor units in pence** (`amountInPence` as a raw `long`), with `currency` as a sibling scalar field. Example: `PaymentRequest.amountInPence = 97500`, `currency = "GBP"`.

The intended platform contract — matching how money is expressed in the real world and how clients (web/mobile) think about amounts — is a **single `amount` + `currency` value object** on the wire, e.g.:

```json
"amount": { "amount": 975.00, "currency": "GBP" }
```

This also mirrors the domain layer, which already models money as the rich `com.hozgan.smartpay.common.model.Money` record.

The E2E smoke suite (`scripts/ci/e2e-smoke-test.sh`, `scripts/ci/e2e-full-lifecycle-test.sh`) was originally authored against the `amount` + `currency` shape; the services evolved to a pence-only DTO shape, producing a contract drift that surfaced as HTTP 500 `HttpMessageNotReadableException: Cannot map null into type long` (`PaymentRequest["amountInPence"]`) whenever the E2E suites ran.

---

## ⚖️ Architectural Tradeoff Analysis

### Option A: Keep pence `long` + scalar currency (Current State)
* **Pros**:
  * No ambiguity for fixed-point arithmetic at the wire level (integers only, no float rounding).
  * Trivial Jackson binding (`long`), no custom (de)serializer needed.
* **Cons**:
  * **Does not match real-world money semantics** the user explicitly requires: amount must read as a normal decimal number, paired with its currency.
  * Exposes implementation detail (minor-unit storage) to API clients.
  * Drifts from the established `Money` domain model and from AGENTS.md layered precision guidance at the DTO boundary.

### Option B: Wire `amount` + `currency` object bound to domain `Money` (Target State)
* **Pros**:
  * Single self-describing money value (`amount`, `currency`) — real-world number format.
  * Maps 1:1 onto `com.hozgan.smartpay.common.model.Money` (`MoneyJsonComponent` already provides JSON (de)serialization infrastructure).
  * Aligns DTO, domain, and E2E contracts.
* **Cons / Migration Effort**:
  * Touches every REST DTO that carries money: `PaymentRequest`, payment responses, invoice/recon payloads, notification parameters, OpenAPI specs, controller tests, and E2E scripts.
  * Must keep the DB/domain minor-unit invariant intact (conversion happens at the mapper boundary via MapStruct/Money factories).
  * Idempotency request-hash computation operates on the raw body; changing the request shape changes hashes (no backward-compatible hashes — acceptable pre-1.0).
  * JSON decimal → pence conversion needs a rounding policy (e.g. HALF_UP, scale 2) that must be centralized in the `Money` binding, not scattered.

**Decision**: Deferred. Recorded here as technical debt; remediate as a dedicated contract-alignment story after the currently open E2E pipeline work lands. Until then the E2E suites must send the *current* pence-shaped payloads (amountInPence + currency + required fields) so the pipeline is green.

---

## 🛠️ Proposed Implementation & Remediation Plan

When scheduling this technical debt item for remediation:

1. **Define the wire type**: introduce (or reuse) a JSON shape `{ "amount": <decimal>, "currency": "<ISO-4217>" }` mapped to `com.hozgan.smartpay.common.model.Money` in every request/response DTO that currently uses `amountInPence` / pence getters (`PaymentRequest`, `PaymentResponse`, invoice pricing DTOs, recon payloads).
2. **Centralize conversion**: add `Money` <-> wire binding (decimal scale-2, `HALF_UP`, validated currency) in `smartpay-common` (`MoneyJsonComponent` / ObjectValue de/serializers) and reuse it everywhere.
3. **Regenerate/update OpenAPI specs** (`docs/openapi/*.yaml`) to the `amount` object schema.
4. **Update E2E scripts** back to the `amount` + `currency` payloads (their original shape) and re-verify AC-1/AC-2/AC-3 semantics.
5. **Update unit/controller tests** that pin `amountInPence` JSON fields.
6. Run full unit + integration suites and the KinD E2E pipeline.

---

## 🎯 Acceptance Criteria for Resolution
1. Every REST request/response money field is a single `amount`+`currency` object on the wire; no public DTO exposes raw pence.
2. Domain/DB layers still store minor units (`BIGINT` pence) — conversion only at the DTO boundary.
3. All E2E suites pass with the `amount` + `currency` payloads against the gateway.
4. OpenAPI specs reflect the money object shape.
