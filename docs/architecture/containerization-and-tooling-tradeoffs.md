# Containerization, Tooling & Kubernetes Architecture Tradeoffs

This document provides a technical tradeoff analysis of key platform architecture decisions implemented across the SmartPay system: container image builds, JVM virtual thread tuning, database migration lifecycle, and Kubernetes integration testing.

---

## 1. Containerization Engine & Build Strategy Tradeoffs

### Evaluated Options

| Criteria | Multi-Stage Distroless (`docker/Dockerfile`) | Prebuilt Distroless (`Dockerfile.prebuilt`) | Google Jib (`jib-maven-plugin`) | Spring Boot Cloud Native Buildpacks |
| :--- | :--- | :--- | :--- | :--- |
| **Java 25 Support** | **Full**: Custom `jlink` JRE from Temurin 25 JDK | **Full**: Reuses cached `jlink` JRE layer | **Blocked**: No official `distroless/java25` base image published | **Lagging**: Paketo buildpacks trail new Java releases |
| **Build Time (CI)** | ~2 min per service (compiles in container) | **~2-3 seconds** (copies pre-built JAR) | Fast (daemonless) | Slow (~1-2 min, heavy builder download) |
| **Local KinD Speed** | Recompiles all dependencies inside Docker | **Instant**: Pre-built fat JAR packaged directly | Fast | Slow |
| **Security Surface** | **Zero shell, zero package manager**, UID 10001 | **Zero shell, zero package manager**, UID 10001 | Zero shell, distroless layers | Varies with CNB builder stack |
| **Trivy CVE Count** | **0 CVEs** (Distroless non-root) | **0 CVEs** (Distroless non-root) | Low | Low to Moderate |
| **Artifact Shape** | Dedicated executable fat JAR per service | Dedicated executable fat JAR per service | Layered image | Layered OCI image |

### Tradeoff Assessment & Decision
* **The Selected Solution**: A dual-mode containerization approach.
  1. `docker/Dockerfile` provides hermetic builds from source for remote CI/CD pipelines.
  2. `docker/Dockerfile.prebuilt` enables rapid local development and KinD cluster importing by leveraging fat JARs built once on the host with `mvn clean package`.
* **Why Not Jib?** Jib cannot construct a custom Java 25 JRE via `jlink` on the fly. It assumes a pre-existing Java 25 JRE image exists on an external registry.
* **Why Not Paketo Buildpacks?** Buildpacks introduce unpredictable runtime user semantics (defaulting to CNB UID `1000`) and consume substantial bandwidth downloading heavy builder images.

---

## 2. JVM & Project Loom Virtual Thread Tuning Tradeoffs

### Evaluated GC & Memory Configurations

| JVM Setting | Selected Value | Alternative Evaluated | Tradeoff Rationale |
| :--- | :--- | :--- | :--- |
| **Garbage Collector** | `-XX:+UseZGC` | `-XX:+UseG1GC` | ZGC guarantees $< 1\text{ ms}$ pause times, preventing stop-the-world latency spikes across high-frequency payment and ledger processing. |
| **Heap Min RAM** | `-XX:InitialRAMPercentage=50.0` | Default (25%) | Pre-allocates heap to prevent JVM memory resizing churn during high-throughput burst traffic. |
| **Heap Max RAM** | `-XX:MaxRAMPercentage=75.0` | Default (25% or 50%) | Allocates 75% of container memory to the heap, leaving 25% for Metaspace, thread stacks, and off-heap Netty/gRPC buffers. |
| **OOM Handling** | `-XX:+ExitOnOutOfMemoryError` | Default (hangs/partial crash) | Guarantees rapid container exit so Kubernetes can automatically restart pods with a clean state. |
| **Entropy Source** | `-Djava.security.egd=file:/dev/./urandom` | Default `/dev/random` | Prevents JVM thread blocking on entropy generation for TLS and cryptographic signatures. |

---

## 3. Database Migration Orchestration Tradeoffs

### Evaluated Approaches

| Architecture Pattern | Selected: K8s Pre-Deployment Job (Sync Wave 1) | In-Process Spring Boot Startup (`spring.flyway.enabled=true`) |
| :--- | :--- | :--- |
| **Deployment Safety** | **High**: Migrations complete and validate before any microservice container starts. | **Risky**: Multiple pod replicas race to acquire Flyway locks simultaneously. |
| **Principle of Least Privilege** | **High**: DDL credentials used exclusively by temporary Flyway Job; microservice pods receive DML-only credentials. | **Low**: Microservice pods require full `ALTER TABLE`/`CREATE TABLE` privileges in production. |
| **Rollback Behavior** | Failed migration stops deployment before new application pods are scheduled. | Application pod enters `CrashLoopBackOff`, potentially leaving DB in a dirty state. |
| **Schema Isolation** | Each service runs its own schema-targeted Flyway Job (`ledger`, `payment`, `invoice`, `notification`). | Harder to isolate credentials per service in a shared database. |

---

## 4. Kubernetes Integration Testing: KinD vs Alternatives

### Evaluated E2E Testing Topologies

| Strategy | KinD Multi-Node Cluster (`scripts/ci/kind-config.yaml`) | Pure Testcontainers Suite | Mocked In-Memory Testing |
| :--- | :--- | :--- | :--- |
| **Fidelity** | **Highest**: Real Kubernetes networking, Ingress controller, DNS, Kustomize overlays, multi-node scheduling. | Moderate: Runs Docker containers, but lacks Kubernetes API, Ingress routing, and K8s DNS resolution. | Low: In-memory mocks do not catch distributed serialization, network timeouts, or schema drift. |
| **Ingress Testing** | Verifies NGINX Ingress controller path routing, CORS, and header propagation. | Cannot test Kubernetes Ingress manifests. | N/A |
| **Resource Footprint** | Moderate (~2-4GB RAM during CI run). | Low to Moderate. | Minimal. |
| **Execution Time** | ~3-5 minutes in CI. | ~30-60 seconds. | < 10 seconds. |

### Conclusion
SmartPay adopts **Test Partitioning**:
* **PR Fast Feedback**: Fast in-memory unit tests (`-Punit`, $< 10\text{s}$) and Testcontainers slice tests (`-Pintegration`, $< 1\text{m}$).
* **Pre-Merge KinD E2E Gate**: Real multi-node KinD cluster exercising Kustomize manifests, Ingress routing, and multi-service idempotency before deployment to Staging.
