# Architecture Decision Records (ADR)

This directory documents the foundational technical and architectural decisions (ADRs) established for the SmartPay platform, detailing context, evaluated alternatives, and consequences.

---

## 📋 ADR Index

| No | Title | Status | Date |
| :--- | :--- | :--- | :--- |
| [**ADR-001**](ADR-001-modern-java-25-and-spring-boot-4.md) | Adoption of Modern Java 25 & Spring Boot 4.1 | **ACCEPTED** | 2026-09-05 |
| [**ADR-002**](ADR-002-uuidv7-primary-keys.md) | Selection of UUIDv7 (RFC 9562) for Database Primary Keys | **ACCEPTED** | 2026-09-05 |
| [**ADR-003**](ADR-003-double-entry-immutable-ledger.md) | Immutable Double-Entry Zero-Sum Ledger Architecture | **ACCEPTED** | 2026-09-05 |
| [**ADR-004**](ADR-004-grpc-internal-service-communication.md) | gRPC (HTTP/2) for Synchronous Inter-Service Communication | **ACCEPTED** | 2026-09-05 |
| [**ADR-005**](ADR-005-transactional-outbox-event-driven.md) | Transactional Outbox Pattern with PostgreSQL SKIP LOCKED | **ACCEPTED** | 2026-09-05 |
| [**ADR-006**](ADR-006-two-tier-distributed-idempotency.md) | Two-Tier Distributed Idempotency via SHA-256 Fingerprinting | **ACCEPTED** | 2026-09-05 |
| [**ADR-007**](ADR-007-redpanda-for-local-development.md) | Adoption of Redpanda for Lightweight Local Kafka Development | **ACCEPTED** | 2026-09-05 |
| [**ADR-008**](ADR-008-event-driven-worker-concurrency-in-kubernetes.md) | Event-Driven Kafka Consumer Groups for Kubernetes Worker Concurrency | **ACCEPTED** | 2026-09-06 |
