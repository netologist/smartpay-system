# ADR-001: Adoption of Modern Java 25 & Spring Boot 4.1

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
SmartPay is a high-throughput, low-latency financial logistics platform requiring microsecond-level balance operations, zero-sum double-entry accounting, and robust concurrency. Traditional JVM architectures suffer from high operating system thread overhead (1MB stack per thread), leading to thread starvation during blocking I/O calls (databases, gRPC RPCs, partner bank APIs). Furthermore, mutable domain models introduce substantial race condition vulnerabilities in concurrent multi-threaded applications.

## Decision
Adopt **Java 25** and **Spring Boot 4.1** across all platform microservices:
1. **Virtual Threads (Project Loom)**: Execute blocking I/O concurrently across lightweight virtual threads without pinning carrier threads or requiring complex reactive programming abstractions.
2. **Records & Immutability**: Define domain models, DTOs, and value objects as Java `record` components to enforce immutability, thread-safety, and boilerplate-free value equality.
3. **Sealed Types & Pattern Matching**: Leverage sealed hierarchies (`SmartpayDomainException`) to enforce compile-time exhaustive error handling via switch expressions.
4. **Spring Boot 4.1**: Native integration with virtual threads, modern webmvc runtime optimizations, and Jakarta EE 11 alignment.

## Alternatives Considered
* **Java 17 / 21**: Virtual Threads were preview/early-stage; Java 25 brings production stabilization and performance optimizations.
* **Go / Rust**: High raw speed, but lacking the mature ecosystem of enterprise banking tools (ISO-20022 parsing, Moneta JSR-354, Spring Data JPA, ArchUnit).

## Consequences
* **Positive**: High throughput under concurrent I/O, low memory consumption, thread-safe domain logic.
* **Negative**: Legacy libraries using `synchronized` blocks that pin carrier threads must be avoided; automated ArchUnit fitness functions enforce these guardrails in CI.
