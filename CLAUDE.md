# SmartPay Assistant Guidelines

## 🌐 Language Policy (Strict Rule)
* **All assistant responses, code comments, commit messages, pull requests, and documentation MUST strictly be in English.**
* Even if queries are prompted in Turkish or another language, reply and generate content exclusively in **English**.

---

## 🏗️ Architecture & Development Rules
* **Language & Runtime**: Java 25 (Virtual Threads, Project Loom), Spring Boot 4.1.
* **Database**: PostgreSQL 16 (B-Tree UUIDv7, Pessimistic Locking).
* **Streaming Broker**: Redpanda (Kafka API on `localhost:9092`).
* **Inter-Service Communication**: gRPC over HTTP/2 (`smartpay-proto`).
* **Object Mapping**: MapStruct 1.6.3 (`@Mapper(componentModel = "spring")`).
* **Web Adapter Layer**: All REST controllers and exception handlers must reside in `.web` package.
* **Monetary Layering**:
  * Database/Entities: `BIGINT` minor units (pence) + ISO-4217 `VARCHAR(3)`.
  * Services/Use Cases: Rich `Money` domain value objects.
  * DTOs: `Money` objects with `@JsonProperty` minor unit getters for dual representation.
  * JSON Serialization: Spring Boot 4.1 `@JacksonComponent` (`MoneyJsonComponent`) via Jackson 3.
* **Testing Standards**:
  * Unit tests tagged `@Tag("unit")` (run with `mvn test -Punit`, $<10\text{s}$).
  * Integration tests tagged `@Tag("integration")` (run with `mvn test -Pintegration`, Testcontainers).
  * Default `mvn test` executes all 150 automated tests.
  * Architecture fitness verified with ArchUnit (`ArchitectureTest.java`).

Refer to `AGENTS.md` and `docs/` for complete architectural specifications and story cards.
