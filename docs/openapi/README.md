# SmartPay OpenAPI 3.1 Specifications

This directory contains the OpenAPI 3.1 REST API specifications for SmartPay microservices and the unified API Gateway.

---

## 📑 Specification Index

| Service | Spec File | Port (HTTP) | Description |
| :--- | :--- | :--- | :--- |
| **API Gateway** | [`gateway-api.yaml`](gateway-api.yaml) | `8080` | **Sole public ingress**: unified API gateway with JWT bearer auth, mandatory `Idempotency-Key`, rate limiting, and longest-prefix path passthrough routing to `/api/v1/payments`, `/api/v1/invoices`, `/api/v1/epod`, `/api/v1/notifications`, `/api/v1/recon`. |
| **Ledger Service** | [`ledger-service-api.yaml`](ledger-service-api.yaml) | `8081` | Double-entry journal balance transfers, atomic hold reserves, and balance queries (internal; not gateway-routed). |
| **Payment Service** | [`payment-service-api.yaml`](payment-service-api.yaml) | `8082` | Idempotent payment initiation, Faster Payments, and VRP settlement tracking. Reached only via gateway `/api/v1/payments`. |
| **Invoice Service** | [`invoice-service-api.yaml`](invoice-service-api.yaml) | `8083` | ePOD delivery proof verification, freight pricing, and invoice management. Reached only via gateway `/api/v1/invoices` + `/api/v1/epod`. |
| **Recon Service** | [`recon-service-api.yaml`](recon-service-api.yaml) | `8085` | CAMT.053 XML / MT940 bank statement upload and reconciliation reporting. Reached only via gateway `/api/v1/recon`. |
| **Notification Svc** | [`notification-service-api.yaml`](notification-service-api.yaml) | `8087` | Multi-channel customer alerts (SMS, Email, Webhooks) and delivery audit trail. Reached only via gateway `/api/v1/notifications`. |
| **gRPC Contracts** | [`smartpay-proto`](../../smartpay-proto/src/main/proto/) | `9091-9095` | Protocol Buffer definitions for high-performance internal RPC (`ledger`, `payment`, `risk`). |
---

## 🛠️ How to View & Test
Import any `.yaml` specification file into Swagger UI, Postman, or Insomnia.

### Launching Swagger UI via Docker:
```bash
docker run -p 8088:8080 -e SWAGGER_JSON=/spec/gateway-api.yaml -v $(pwd)/docs/openapi:/spec swaggerapi/swagger-ui
```
Then navigate to `http://localhost:8088` in your browser.
