# STORY-006: API Gateway & Dağıtık Idempotency Filtresi

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-gateway`
* **Öncelik**: P2 (Sistem Giriş Kapısı & Güvenlik)
* **İlişkili Veritabanı Tabloları**: `idempotency_records` (`V5`)
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `TenantId`, `IdempotencyKey`
  * `IdempotencyStatus`
  * `IdempotencyConflictException`, `RequestHashMismatchException`

---

## 🎯 Kullanıcı Hikayesi
> **Bir** API Gateway olarak,  
> **Dış dünyadan gelen finansal HTTP isteklerini** (POST/PUT) `Idempotency-Key` başlığı ve SHA-256 gövde parmak iziyle karşılamak,  
> **Böylece** ağ gecikmesi veya istemci tarafındaki yeniden denemelerde (retry) arka uç servislerinin mükerrer tetiklenmesini en dış sınırda engellemek istiyorum.

---

## 📐 Mimari ve Filtre Kuralları

1. **Header Kontrolü**:
   * Finansal işlem isteklerinde (`/api/v1/payments/**`, `/api/v1/invoices/**`) `Idempotency-Key` başlığı zorunludur.
   * Başlık yoksa HTTP 400 Bad Request dönülmelidir.

2. **Gövde Parmak İzi (Fingerprinting)**:
   * Gelen JSON gövdesi SHA-256 ile özetlenir (`request_hash`).
   * Aynı anahtar ile farklı bir gövde gönderilirse `RequestHashMismatchException` (HTTP 422) verilir.

3. **Response Caching**:
   * Arka uç servisinden dönen HTTP status kodu ve JSON gövdesi `idempotency_records` tablosuna yazılır. Sonraki aynı isteklerde doğrudan bu kaydedilen yanıt dönülür.

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: Eksik Başlık Reddi
* **Given**: `Idempotency-Key` başlığı içermeyen bir ödeme isteği geldiğinde,
* **When**: Gateway filtre çalıştığında,
* **Then**: İstek arka uç servisine iletilmemeli, HTTP 400 Bad Request dönmelidir.

### AC-2: Başarılı İstek Önbelleklemesi
* **Given**: İlk kez gelen geçerli bir ödeme isteğinde,
* **When**: Arka uç servisi 201 Created döndüğünde,
* **Then**: Gateway yanıtı `idempotency_records` tablosuna `COMPLETED` olarak kaydetmeli ve istemciye iletmelidir.

### AC-3: Mükerrer İstekte Anında Yanıt
* **Given**: Aynı anahtar ve aynı gövdeyle ikinci bir istek geldiğinde,
* **When**: Gateway filtre çalıştığında,
* **Then**: Arka uç servisi hiç çağrılmamalı, veritabanındaki kayıtlı yanıt HTTP başlıklarına `X-Cache: IDEMPOTENT-HIT` eklenerek dönmelidir.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-gateway/src/main/java/com/hozgan/smartpay/gateway/
├── filter/
│   ├── IdempotencyGatewayFilter.java   // Servlet / WebFilter filtresi
│   └── RequestCachingWrapper.java      // Gövdeyi birden fazla okuyabilmek için wrapper
└── service/
    └── GatewayIdempotencyService.java  // Veritabanı okuma/yazma servis katmanı
```
