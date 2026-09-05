# SmartPay OpenAPI 3.1 Spesifikasyonları

Bu dizin, SmartPay platformunun dışa ve içe açık REST API uç noktalarının OpenAPI 3.1 spesifikasyonlarını içerir.

---

## 📑 Spesifikasyon Listesi

| Servis | Dosya | Port (HTTP) | Açıklama |
| :--- | :--- | :--- | :--- |
| **API Gateway** | [`gateway-api.yaml`](gateway-api.yaml) | `8080` | Birleşik dış ağ API'si, JWT yetkilendirme, Idempotency-Key zorunluluğu. |
| **Invoice Service** | [`invoice-service-api.yaml`](invoice-service-api.yaml) | `8083` | ePOD teslimat kanıtı doğrulama, navlun hesaplama ve fatura kesimi. |
| **Payment Service** | [`payment-service-api.yaml`](payment-service-api.yaml) | `8082` | İdempotent ödeme başlatma, Faster Payments ve VRP durumu. |
| **Recon Service** | [`recon-service-api.yaml`](recon-service-api.yaml) | `8084` | CAMT.053 XML / MT940 ekstre yükleme ve mutabakat raporlama. |

---

## 🛠️ Nasıl Kullanılır?
Swagger UI veya Postman üzerinde görüntülemek için ilgili `.yaml` dosyasını içe aktarabilirsiniz.
Örnek (Swagger Editor):
```bash
# Docker ile hızlı Swagger UI ayağa kaldırma:
docker run -p 8088:8080 -e SWAGGER_JSON=/spec/gateway-api.yaml -v $(pwd)/docs/openapi:/spec swaggerapi/swagger-ui
```
Ardından tarayıcıda `http://localhost:8088` adresine gidiniz.
