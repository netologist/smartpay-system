# ADR-007: Adoption of Redpanda for Lightweight Local Kafka Development

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
SmartPay uses the Apache Kafka wire protocol for event-driven architecture and the Transactional Outbox pattern. However, running standard JVM-based Apache Kafka in local developer environments via Docker imposes significant resource costs:
1. **High Memory Overhead**: Apache Kafka requires ~1.5 GB – 2 GB of RAM per broker due to JVM heap allocations. Running 10 Spring Boot microservices, PostgreSQL, and Kafka concurrently exhausts developer workstation RAM.
2. **Slow Boot Times**: Initializing KRaft or ZooKeeper metadata takes 20 to 30 seconds before containers become healthy.
3. **Apple Silicon Overhead**: JVM emulation and container layering consume unnecessary battery and CPU cycles on macOS M-series machines.

## Decision
Adopt **Redpanda** for local development (`docker-compose.yml`) and local integration tests:
1. **C++20 & Seastar Engine**: Redpanda implements a thread-per-core, zero-GC architecture.
2. **Ultra-Low Resource Footprint**: Consumes only ~150 MB – 300 MB of RAM and achieves healthy status in under 1 second.
3. **100% Kafka API Compatibility (Drop-in Replacement)**: Applications interact exclusively via standard `spring-kafka` on port `9092`. No Redpanda-specific dependencies exist in application source code.
4. **Built-in Redpanda Console**: Provides an out-of-the-box web UI on port `8090` for inspecting topics, consumer groups, and partition offsets.

## Production Alignment
Application code strictly adheres to the standard Kafka API (`spring-kafka`, `ProducerRecord`, `@KafkaListener`). In production environments (Kubernetes), infrastructure can point seamlessly to:
* AWS Managed Streaming for Apache Kafka (MSK),
* Strimzi Apache Kafka Operator,
* Confluent Cloud or Redpanda Cloud,

without any code modifications, simply by setting `spring.kafka.bootstrap-servers`.

## Consequences
* **Positive**: 10x lower local RAM consumption, instant local boot time, native ARM64 speed on Apple Silicon, zero vendor lock-in.
* **Negative**: Developers must recognize that local Redpanda implements the exact Kafka protocol without running a JVM broker.
