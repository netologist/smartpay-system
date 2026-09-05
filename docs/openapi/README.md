# SmartPay OpenAPI 3.1 Specifications

This directory contains the OpenAPI 3.1 REST API specifications for SmartPay microservices and the unified API Gateway.

---

## 📑 Specification Index

| Service | Spec File | Port (HTTP) | Description |
| :--- | :--- | :--- | :--- |
| **API Gateway** | [`gateway-api.yaml`](gateway-api.yaml) | `8080` | Unified public ingress API, JWT bearer auth, mandatory Idempotency-Key header. |
| **Invoice Service** | [`invoice-service-api.yaml`](invoice-service-api.yaml) | `8083` | ePOD delivery proof verification, freight pricing, and invoice management. |
| **Payment Service** | [`payment-service-api.yaml`](payment-service-api.yaml) | `8082` | Idempotent payment initiation, Faster Payments, and VRP settlement tracking. |
| **Recon Service** | [`recon-service-api.yaml`](recon-service-api.yaml) | `8084` | CAMT.053 XML / MT940 bank statement upload and reconciliation reporting. |

---

## 🛠️ How to View & Test
Import any `.yaml` specification file into Swagger UI, Postman, or Insomnia.

### Launching Swagger UI via Docker:
```bash
docker run -p 8088:8080 -e SWAGGER_JSON=/spec/gateway-api.yaml -v $(pwd)/docs/openapi:/spec swaggerapi/swagger-ui
```
Then navigate to `http://localhost:8088` in your browser.
