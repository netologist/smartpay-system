# TECH-003: Ephemeral KinD Cluster & Cloud-Native E2E Integration Suite

## 📌 Story Overview
* **Target Directory**: `.github/workflows/e2e-kind.yml`, `scripts/ci/`, `tests/e2e/`
* **Priority**: P1 (End-to-End Environment Verification)
* **Components Covered**:
  * Local KinD (Kubernetes in Docker) Cluster Config (1 Control-Plane, 2 Workers)
  * Infrastructure Bootstrap: PostgreSQL 16 StatefulSet & Flyway Migration Job (Sync Wave 1)
  * Message Broker: Single-Node Redpanda (Kafka wire-compatible)
  * Automated Multi-Service Ingress & Idempotency Smoke Test Suite

---

## 🎯 User Story
> **As a** Release Engineer,  
> **I want to** provision an ephemeral KinD Kubernetes cluster in CI, deploy all microservices via Kustomize, and execute end-to-end multi-service scenarios,  
> **So that** distributed integration bugs (networking, DNS, outbox polling, gRPC connectivity) are caught in real Kubernetes before staging deployment.

---

## 🔄 KinD Execution Flow in CI

```
1. KinD Cluster Provisioning
   │ Spin up kind cluster using scripts/ci/kind-config.yaml with ingress port-mappings (80/443).
   ▼
2. Platform Infrastructure Deployment
   │ Deploy PostgreSQL 16 StatefulSet + Service.
   │ Deploy Redpanda (Kafka-compatible) StatefulSet + Service.
   ▼
3. Database Migration Execution (Sync Wave 1)
   │ Execute smartpay-flyway-migration Job. Wait for completion (exit code 0).
   ▼
4. Load Locally-Built Images into KinD
   │ kind load docker-image smartpay-gateway:local smartpay-payment-service:local ...
   ▼
5. Deploy Microservices via Kustomize
   │ kubectl apply -k k8s/overlays/dev
   │ kubectl wait --namespace smartpay --for=condition=ready pod --all --timeout=180s
   ▼
6. Run Automated E2E Test Suite
   │ Execute scripts/ci/e2e-smoke-test.sh:
   │ - Scenario 1: POST /api/v1/payments/initiate with Idempotency-Key
   │ - Scenario 2: Repeat request -> Assert X-Cache: IDEMPOTENT-HIT
   │ - Scenario 3: Repeat with altered payload -> Assert HTTP 422
   │ - Scenario 4: Query outbox table -> Assert event marked processed_at
```

---

## ✅ Acceptance Criteria (AC)

### AC-1: Ephemeral KinD Provisioning & Ingress Routing
* **Given**: A GitHub Actions runner with Docker,
* **When**: `e2e-kind.yml` triggers,
* **Then**: A multi-node KinD cluster is created, and the ingress controller routes traffic to `smartpay-gateway`.

### AC-2: Automated Database Migration Pre-Requisite
* **Given**: PostgreSQL started in KinD,
* **When**: `smartpay-flyway-migration` Job runs,
* **Then**: Tables `transactional_outbox`, `idempotency_records`, and `payments` are created before microservice pods start.

### AC-3: Full-Stack E2E Payment Journey Verification
* **Given**: All microservice pods in `Ready` status,
* **When**: The automated E2E test script executes,
* **Then**: Payment initiation, two-tier idempotency, ledger hold, and outbox lifecycle pass with zero HTTP 5xx errors.
