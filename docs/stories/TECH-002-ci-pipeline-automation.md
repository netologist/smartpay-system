# TECH-002: Enterprise CI Pipeline Automation with GitHub Actions

## 📌 Story Overview
* **Target Directory**: `.github/workflows/`
* **Priority**: P0 (Continuous Integration & Quality Gates)
* **Components Covered**:
  * `ci-pr-validation.yml`: Fast PR quality gate (Java 25, Unit `-Punit`, Integration `-Pintegration`, ArchUnit)
  * `docker-build-push.yml`: Continuous Delivery image builder, Trivy vulnerability scanner, GHCR registry publishing

---

## 🎯 User Story
> **As a** Software Engineering Team Lead,  
> **I want to** enforce automated GitHub Actions CI pipelines with Maven caching, JUnit test partitioning, and security vulnerability scanning,  
> **So that** broken builds, architectural violations, and critical CVEs are rejected before merging into `main`.

---

## 🔄 CI Pipeline Execution Workflow

```
1. Pull Request Trigger (ci-pr-validation.yml)
   │ Runs on: pull_request to main / develop
   ▼
2. Environment Setup & Dependency Caching
   │ - Setup Java 25 (Eclipse Temurin)
   │ - Cache ~/.m2/repository using pom.xml hash keys
   ▼
3. Gate 1: Fast In-Memory Unit Suite
   │ mvn test -Punit (executes in < 10s across all 11 modules)
   ▼
4. Gate 2: Architecture Fitness Verification
   │ ArchUnit fitness tests (package boundaries, immutability, no framework leak)
   ▼
5. Gate 3: Testcontainers Integration Suite
   │ mvn test -Pintegration (PostgreSQL 16, gRPC channels, MockMvc)
   ▼
6. Main Branch Merge Trigger (docker-build-push.yml)
   │ Runs on: push to main
   ▼
7. Multi-Module Container Build & Security Scan
   │ - Build Distroless images via Docker Buildx
   │ - Scan with Trivy (Fail on CRITICAL / HIGH CVEs)
   │ - Push to ghcr.io/hozgan/smartpay/* with git SHA and latest tags
```

---

## ✅ Acceptance Criteria (AC)

### AC-1: Fast PR Validation Feedback Loop
* **Given**: A newly opened Pull Request,
* **When**: The CI workflow triggers,
* **Then**: The unit test gate finishes in $< 3\text{ minutes}$ using warmed Maven cache.

### AC-2: Zero Architectural Regression
* **Given**: A PR that introduces an illegal dependency (e.g. controller outside `.web` or Jackson on domain model),
* **When**: The CI pipeline runs,
* **Then**: ArchUnit fails the build and blocks the PR from merging.

### AC-3: Automated Container Build & Trivy Security Gate
* **Given**: Code merged into `main`,
* **When**: `docker-build-push.yml` executes,
* **Then**: Microservice images are scanned for vulnerabilities and pushed to GHCR only if zero `CRITICAL` CVEs exist.
