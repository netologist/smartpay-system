# ADR-004: gRPC (HTTP/2) for Synchronous Inter-Service Communication

## Status
**ACCEPTED**

## Date
2026-09-05

## Context
SmartPay microservices (such as `payment-service` and `ledger-service`) engage in high-frequency, low-latency synchronous RPC calls. Using traditional REST (JSON over HTTP/1.1):
* Incurs high TCP/TLS handshake overhead per connection.
* Wastes CPU cycles and network bandwidth parsing and serializing verbose text-based JSON.
* Lacks compile-time schema validation across language or module boundaries, allowing breaking API drift.

## Decision
Standardize on **gRPC over HTTP/2** with Protocol Buffers for all synchronous inter-service communication:
1. **Centralized Contract Repository (`smartpay-proto`)**: All message formats and service definitions are maintained in `.proto` files, producing strongly-typed Java stubs at build time.
2. **HTTP/2 Multiplexing**: Multiple concurrent requests flow over a single persistent TCP connection.
3. **Binary Serialization**: Protocol Buffers yield 5-10x faster serialization and significantly smaller payloads than JSON.

## Alternatives Considered
* **REST (JSON/HTTP 1.1)**: Retained at the public API Gateway for broad client compatibility, but excluded from internal service meshes.
* **Apache Thrift**: Lacks the broad cloud-native ecosystem and Kubernetes ingress tooling available for gRPC.

## Consequences
* **Positive**: Sub-2ms internal RPC latency, guaranteed compile-time contract compatibility, native support for bidirectional streaming.
* **Negative**: Browsers cannot call gRPC directly without gRPC-Web or an API Gateway translation proxy; developers must understand protobuf code generation lifecycles.
