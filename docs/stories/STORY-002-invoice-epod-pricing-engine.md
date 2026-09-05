# STORY-002: Navlun Faturası & ePOD Fiyatlandırma Motoru

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-invoice-service`
* **Öncelik**: P1 (Lojistik Faturalandırma)
* **İlişkili Veritabanı Tabloları**: `epod_records`, `invoices` (`V3`)
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `Money` (Baz navlun, yakıt sürşarjı, KDV tutarları)
  * `InvoicePricing` (Fatura fiyatlama değişmezi doğrulaması)
  * `LoadId`, `CarrierId`, `ShipperId`, `InvoiceId` (Kimlikler)
  * `GeoLocation` (Enlem/Boylam), `SignatureHash` (SHA-256)
  * `VehicleType` (`VAN`, `LUTON`, `7_5T`, `ARTIC`), `InvoiceStatus`
  * `InvalidEpodSignatureException`, `DuplicateLoadException`, `InvoiceAlreadySettledException`
  * `EpodVerifiedEvent`, `InvoiceIssuedEvent`

---

## 🎯 Kullanıcı Hikayesi
> **Bir** Navlun Faturalandırma Sistemi olarak,  
> **Sürücülerin teslimat kanıtlarını (ePOD)** kriptografik ve coğrafi olarak doğrulamak, kilometre ve araç tipine göre yakıt sürşarjı ve KDV içeren faturaları otomatik hesaplamak istiyorum,  
> **Böylece** navlun teslimatları gerçekleşir gerçekleşmez insan hatasından arındırılmış faturalar üretilsin ve faktoring ödeme sürecine aktarılsın.

---

## 📐 Mimari ve Fiyatlandırma Kuralları

1. **Fiyatlandırma Formülü**:
   Navlun faturasının toplam tutarı değişmezi:
   $$\text{Total} = \text{Base Amount} + \text{Fuel Surcharge} + \text{VAT}$$
   * **Base Amount**: $\text{Mileage (Miles)} \times \text{Vehicle Rate Per Mile}$
     * `VAN`: £1.50 / mil
     * `LUTON`: £2.00 / mil
     * `7_5T`: £2.75 / mil
     * `ARTIC`: £3.50 / mil
   * **Fuel Surcharge**: Baz tutarın %12'si (örn. `baseAmount.percent(12)`).
   * **VAT (KDV)**: İngiltere standart navlun KDV oranı %20 (`(base + fuel).percent(20)`).
   * Tüm bu matematik `InvoicePricing` nesnesi tarafından garanti edilir.

2. **ePOD Doğrulama Değişmezi**:
   * Teslimat fotoğrafı S3 linki dolu olmalıdır.
   * `SignatureHash` 64 karakterlik geçerli SHA-256 hex dizesi olmalıdır.
   * Aynı `load_id` için mükerrer ePOD gelirse `DuplicateLoadException` fırlatılmalıdır.

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: Teslimat Kanıtı (ePOD) Doğrulama ve Kaydı
* **Given**: Sürücü teslimatı tamamlayıp koordinat (51.5074, -0.1278), imza hash'i ve fotoğraf yüklediğinde,
* **When**: `verifyEpod(command)` çağrıldığında,
* **Then**: `epod_records` tablosuna `verified = true` olarak kaydedilmeli ve `EpodVerifiedEvent` üretilmelidir.
* **And**: İmza formatı bozuksa `InvalidEpodSignatureException` fırlatılmalıdır.

### AC-2: Otomatik Fatura Hesaplama ve Kesimi
* **Given**: Doğrulanmış bir yük için 100 mil ve `LUTON` araç tipi seçildiğinde,
* **When**: `generateInvoice(loadId, shipperId, carrierId, vehicleType, mileage)` çağrıldığında:
  * Baz Tutar: $100 \times £2.00 = £200.00$
  * Yakıt Sürşarjı: $£200.00 \times 0.12 = £24.00$
  * KDV (%20): $(£200.00 + £24.00) \times 0.20 = £44.80$
  * Toplam Fatura: $£200.00 + £24.00 + £44.80 = £268.80$
* **Then**: `invoices` tablosuna `status = EPOD_VERIFIED` olarak kaydedilmeli ve `InvoiceIssuedEvent` fırlatılmalıdır.

### AC-3: Fatura Statü Geçiş Güvenliği
* **Given**: Zaten `SETTLED` (ödenmiş) statüsündeki bir fatura için,
* **When**: İptal veya tutar güncelleme isteği gelirse,
* **Then**: İşlem reddedilmeli ve `InvoiceAlreadySettledException` fırlatılmalıdır.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-invoice-service/src/main/java/com/hozgan/smartpay/invoice/
├── service/
│   ├── EpodService.java                // ePOD kabul ve doğrulama
│   ├── FreightPricingCalculator.java   // Araç tipi ve mil bazlı fiyatlama motoru
│   ├── InvoiceService.java             // Fatura oluşturma, statü yönetimi
│   └── impl/
│       ├── EpodServiceImpl.java
│       ├── FreightPricingCalculatorImpl.java
│       └── InvoiceServiceImpl.java
├── web/
│   ├── EpodController.java             // REST endpoint: POST /api/v1/epod/verify
│   └── InvoiceController.java          // REST endpoint: GET/POST /api/v1/invoices
└── dto/
    ├── VerifyEpodRequest.java
    └── CreateInvoiceRequest.java
```
