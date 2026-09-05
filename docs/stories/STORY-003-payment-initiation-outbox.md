# STORY-003: Ödeme Başlatma & Transactional Outbox Motoru

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-payment-service`
* **Öncelik**: P1 (Ödeme & Dağıtık Tutarlılık)
* **İlişkili Veritabanı Tabloları**: `transactional_outbox` (`V4`), `idempotency_records` (`V5`)
* **İlişkili gRPC Bağımlılığı**: `smartpay-ledger-service` (`smartpay-proto/src/main/proto/ledger.proto`)
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `Money` (Ödeme tutarı)
  * `AccountId`, `TenantId`, `IdempotencyKey`, `EndToEndId`
  * `IdempotencyStatus` (`PROCESSING`, `COMPLETED`, `FAILED`)
  * `IdempotencyConflictException`, `RequestHashMismatchException`, `DuplicateTransactionException`
  * `OutboxEvent` (At-least-once event kaydı)

---

## 🎯 Kullanıcı Hikayesi
> **Bir** Ödeme İletim Sistemi olarak,  
> **Faster Payments ve VRP ödeme emirlerini** çift katmanlı idempotency kontrolüyle başlatmak, Ledger servisine gRPC ile bloke koydurmak ve Kafka event'lerini transactional outbox tablosuna yazmak istiyorum,  
> **Böylece** ağ kesintilerinde çift çekim yaşanmasın ve veri kaybı olmadan en az bir kez (at-least-once) mesaj teslimi garanti edilsin.

---

## 📐 Mimari ve Dağıtık Tasarım Kuralları

1. **Transactional Outbox Deseni (Dual-Write Problemini Önleme)**:
   * Bir veritabanı transaction'ı içinde hem iş tablosu güncellenmeli hem de `transactional_outbox` tablosuna event satırı (`JSONB` payload) yazılmalıdır.
   * Kafka'ya mesaj doğrudan web isteği anında **atılmaz**; outbox tablosuna yazılır. Bir arka plan worker'ı bu tabloyu okuyup Kafka'ya basar.

2. **İki Aşamalı Dağıtık Idempotency**:
   * **Adım 1**: İstek geldiğinde request body SHA-256 hash'i hesaplanır. `idempotency_records` tablosuna `PROCESSING` statüsünde kilit atılır (`PRIMARY KEY (tenant_id, idempotency_key)`).
   * **Adım 2**: Eğer aynı anahtarla işlem sürüyorsa `IdempotencyConflictException`, farklı gövdeyle geldiyse `RequestHashMismatchException` fırlatılır.
   * **Adım 3**: İşlem başarıyla bitince statü `COMPLETED` yapılır ve response gövdesi saklanır. Mükerrer isteklerde aynı response hemen dönülür.

3. **Ledger gRPC İletişimi**:
   * Ödeme emri çıkmadan önce Ledger servisinin `HoldFunds` metodu çağrılarak borçlu hesaba bloke koyulur.

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: İdempotent Ödeme Başlatma
* **Given**: 500 GBP tutarında geçerli bir ödeme emri geldiğinde,
* **When**: `initiatePayment(request)` çağrıldığında,
* **Then**: `idempotency_records` tablosunda kayıt oluşturulmalı, Ledger gRPC üzerinden `HoldFunds` çağrılmalı ve `transactional_outbox` tablosuna `PAYMENT_INITIATED` eventi yazılmalıdır.

### AC-2: Mükerrer İstek Korunması
* **Given**: İlk istek tamamlandıktan sonra aynı `idempotency_key` ve aynı payload ile ikinci bir istek geldiğinde,
* **When**: `initiatePayment` çağrıldığında,
* **Then**: Ledger'a tekrar gidilmemeli, outbox'a yeni event atılmamalı; önceki başarılı sonuç dönmelidir.

### AC-3: Outbox Polling Güvencesi (SKIP LOCKED)
* **Given**: `transactional_outbox` tablosunda işlenmemiş (`processed_at IS NULL`) event'ler varken,
* **When**: Outbox worker çalıştığında,
* **Then**: Kayıtlar `ORDER BY created_at ASC FOR UPDATE SKIP LOCKED` ile kilitlenip okunmalı, Kafka'ya iletildikten sonra `processed_at = NOW()` olarak işaretlenmelidir.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-payment-service/src/main/java/com/hozgan/smartpay/payment/
├── service/
│   ├── PaymentService.java             // Ödeme başlatma, tamamlama, iptal
│   ├── IdempotencyService.java         // 2-tier SHA-256 kilit mekanizması
│   └── impl/
│       ├── PaymentServiceImpl.java
│       └── IdempotencyServiceImpl.java
├── grpc/
│   └── client/
│       └── LedgerGrpcClient.java       // Ledger gRPC stub çağrı sarmalayıcısı
├── worker/
│   └── OutboxEventPublisherWorker.java // @Scheduled sanal thread tabanlı outbox okuyucu
└── web/
    └── PaymentController.java          // REST POST /api/v1/payments/initiate
```
