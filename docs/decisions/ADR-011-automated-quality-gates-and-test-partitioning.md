# ADR-011: Automated Multi-Stage Quality Gates, ArchUnit Fitness Rules & Test Partitioning

## Status
**ACCEPTED**

## Date
2026-09-07

## Context
SmartPay operates as a mission-critical financial logistics platform comprising 8 microservices, 2 shared libraries, and over 250 automated tests. The platform enforces strict architectural invariants:
1. **Concurrency Safety**: Project Loom (Java 25 Virtual Threads) prohibits `synchronized` blocks or methods to eliminate carrier thread pinning.
2. **Layer Isolation & Packaging**: All HTTP endpoints and REST controllers must reside strictly under the `.web` package (never `.controller`).
3. **Immutability & Dependency Injection**: Domain models and DTOs must be immutable records, and dependencies must be injected via constructors (no `@Autowired` field injection).
4. **Zero-Tolerance Security Baseline**: Container images must contain zero `CRITICAL` Common Vulnerabilities and Exposures (CVEs).

Without automated enforcement in the build and pull request pipeline, these standards suffer from gradual architectural erosion, formatting inconsistencies, and broken builds.

## Decision
Implement a multi-tier automated quality pipeline integrated into the Maven lifecycle and GitHub Actions CI/CD workflows:

### 1. Test Partitioning via Maven Profiles (`-Punit` vs `-Pintegration`)
* **Unit Quality Gate (`mvn verify -Punit`)**:
  * Executes fast in-memory unit tests, domain pricing math, MapStruct mappers, calculation engines, and ArchUnit architecture fitness rules.
  * Runs in $< 50\text{ seconds}$ across all 11 modules with **zero Docker/container footprint**.
* **Integration Gate (`mvn test -Pintegration`)**:
  * Executes full-stack slice tests against Testcontainers PostgreSQL 16, HTTP/2 gRPC channels on Virtual Threads, WireMock HTTP providers, and MockMvc controllers.
  * Runs in a dedicated pipeline stage, preserving fast feedback on unit PR gates.

### 2. Automated Architecture Fitness Testing (`archunit-junit5`)
* Implemented `ArchitectureTest.java` across all 8 microservice modules and `smartpay-common`:
  * `classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..web..")`
  * `noFields().should().beAnnotatedWith(Autowired.class)` (constructor injection enforcement)
  * `noMethods().should().haveModifier(JavaModifier.SYNCHRONIZED)` (virtual thread carrier non-pinning)
  * `GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS` (structured SLF4J logging)

### 3. Code Formatting & Hygiene (`spotless-maven-plugin:2.44.3`)
* Standardized whitespace trimming, POSIX trailing newline enforcement, and unused import stripping.
* `spotless:check` is bound to the `verify` phase to reject formatting regressions in CI.
* `spotless:apply` allows developers to auto-format the entire codebase on demand.

### 4. Code Coverage Tracking & HTML Publishing (`jacoco-maven-plugin:0.8.15`)
* Uses JaCoCo `0.8.15` to support Java 25 class file major version 69.
* Automatically attaches runtime agent (`prepare-agent`) and generates HTML reports under `${module}/target/site/jacoco/index.html` during the `verify` phase.
* GitHub Actions uploads coverage reports and Surefire test HTML reports as downloadable artifacts with 7-day retention.

### 5. Automated Container Vulnerability Scanning (`aquasecurity/trivy-action`)
* Continuous Delivery pipeline (`docker-build-push.yml`) scans all distroless container images prior to publishing to GHCR.
* Enforces `exit-code: 1` on `CRITICAL` severity CVEs, blocking deployment if unpatched vulnerabilities exist.

## Consequences
* **Positive**:
  * **Continuous Architectural Compliance**: Violations of Loom virtual thread safety, constructor injection, or packaging standards immediately fail the build before human review.
  * **Rapid Developer Feedback**: Partitioned unit suite delivers feedback in under a minute without launching container runtimes.
  * **Audit Transparency**: Detailed HTML test execution and JaCoCo coverage reports are preserved in GitHub Actions for every pull request.
  * **Zero Critical CVE Guarantee**: Container vulnerabilities in third-party libraries (e.g. embedded servlet containers) are blocked prior to registry publication.
* **Negative**:
  * Running `mvn verify -Punit` adds ~15-20 seconds to local build cycles due to JaCoCo bytecode analysis and ArchUnit classpath inspection.
