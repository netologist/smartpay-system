# TECH-001: Cloud-Native Containerization & Kubernetes Kustomize Engine

## 📌 Story Overview
* **Target Directory**: `docker/`, `k8s/`
* **Priority**: P0 (Platform Infrastructure Foundation)
* **Components Covered**:
  * Multi-Stage Distroless Dockerfiles (Java 25, Virtual Threads, Non-Root)
  * Kustomize Declarative Manifest Hierarchy (`base/`, `overlays/dev/`, `overlays/staging/`, `overlays/prod/`)
  * Actuator HTTP & gRPC Health Probes (Liveness, Readiness, Startup)
  * Least-Privilege Pod Security Standards & Resource Quotas

---

## 🎯 User Story
> **As a** DevOps & Cloud Platform Engineer,  
> **I want to** containerize all SmartPay microservices with secure, non-root multi-stage Dockerfiles and define parameterized Kubernetes manifests using Kustomize,  
> **So that** microservices deploy deterministically across Dev, Staging, and Production clusters with zero drift and optimal JVM Virtual Thread performance.

---

## 🏗️ Kustomize Directory Layout

```
k8s/
├── base/
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── network-policy.yaml
│   ├── common-configmap.yaml
│   ├── services/
│   │   ├── ledger-service/ (deployment.yaml, service.yaml)
│   │   ├── payment-service/ (deployment.yaml, service.yaml)
│   │   ├── invoice-service/ (deployment.yaml, service.yaml)
│   │   └── gateway/ (deployment.yaml, service.yaml, ingress.yaml)
│   └── migrations/
│       └── flyway-migration-job.yaml
└── overlays/
    ├── dev/
    │   ├── kustomization.yaml
    │   └── patches/ (replicas=1, ephemeral resources, debug logs)
    ├── staging/
    │   ├── kustomization.yaml
    │   └── patches/ (replicas=2, realistic limits)
    └── prod/
        ├── kustomization.yaml
        └── patches/ (hpa.yaml, pdb.yaml, strict network policies)
```

---

## ✅ Acceptance Criteria (AC)

### AC-1: Hardened Distroless Non-Root Container Images
* **Given**: A Java 25 microservice,
* **When**: Built with the multi-stage Dockerfile,
* **Then**: The resulting image runs as non-root UID `10001`, uses distroless runtime, and contains only the compiled jar and JRE without package managers (`apt`, `apk`) or shell (`sh`, `bash`).

### AC-2: Valid Kustomize Overlays
* **Given**: The `k8s/` directory structure,
* **When**: `kustomize build k8s/overlays/dev`, `staging`, and `prod` are executed,
* **Then**: Valid Kubernetes manifests are generated without syntax errors or missing references.

### AC-3: Kubernetes Probes & Virtual Thread Tuning
* **Given**: Microservice deployments,
* **When**: Inspected in Kubernetes,
* **Then**: Every container specifies:
  - Startup probe (`/actuator/health/liveness`, initialDelay=5s, period=5s, failureThreshold=12)
  - Liveness probe (`/actuator/health/liveness`, period=10s)
  - Readiness probe (`/actuator/health/readiness`, period=5s)
  - Environment variables `-XX:+UseZGC -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0`
