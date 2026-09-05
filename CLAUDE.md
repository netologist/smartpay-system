# SmartPay Assistant Guidelines

## 🌐 Language Policy (Strict Rule)
* **All assistant responses, code comments, commit messages, and documentation MUST strictly be in English.**
* Even if queries are prompted in Turkish or another language, reply and generate content exclusively in **English**.

---

## 🏗️ Architecture & Development Rules
* **Language & Runtime**: Java 25 (Virtual Threads, Project Loom), Spring Boot 4.1.
* **Database**: PostgreSQL 16 (B-Tree UUIDv7, Pessimistic Locking).
* **Streaming Broker**: Redpanda (Kafka API on `localhost:9092`).
* **Inter-Service Communication**: gRPC over HTTP/2 (`smartpay-proto`).
* **Domain Models**: Use `smartpay-common` (`Money`, `UuidV7`, `AccountId`, `SmartpayDomainException`).
* **Testing**: JUnit 5, AssertJ, ArchUnit 1.4.1.

Refer to `AGENTS.md` and `docs/` for complete architectural specifications.
