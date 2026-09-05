# STORY-004: Taşımacı Faktoring & Erken Ödeme Worker'ı

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-payout-worker`
* **Öncelik**: P1 (Faktoring & Likidite Motoru)
* **İlişkili Servisler**: `smartpay-invoice-service`, `smartpay-payment-service`
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `Money` (Hak ediş ve faktoring komisyon hesaplaması)
  * `CarrierId`, `InvoiceId`, `TransactionId`
  * `FactoringPayoutApprovedEvent`
  * Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`)

---

## 🎯 Kullanıcı Hikayesi
> **Bir** Lojistik Faktoring Motoru olarak,  
> **Teslimatı doğrulanmış yük faturalarını tarayarak**, risk skorunu kontrol etmek ve taşımacıya 30-90 gün beklemek yerine %2.5 platform komisyonu kesintisiyle anında erken ödeme çıkarmak istiyorum,  
> **Böylece** taşımacılar için nakit akışı likiditesi sağlansın, platform faktoring komisyon geliri elde etsin.

---

## 📐 Mimari ve İş Mantığı Kuralları

1. **Faktoring Komisyon Hesaplama Formülü**:
   $$\text{Factoring Fee} = \text{Invoice Total} \times 0.025 \quad (2.5\%)$$
   $$\text{Payout Amount} = \text{Invoice Total} - \text{Factoring Fee}$$
   Örnek: 1000 GBP fatura için:
   * Faktoring Komisyonu (Platform Geliri): $£1000 \times 0.025 = £25.00$
   * Taşımacıya Yatırılacak Erken Tutar: $£1000 - £25 = £975.00$

2. **Virtual Thread Worker Havuzu**:
   * Her bir ödeme emri bağımsız bir sanal thread (`Thread.ofVirtual()`) içinde çalıştırılmalıdır.
   * Bloklayıcı gRPC/HTTP çağrılarında işletim sistemi carrier thread'leri kilitlenmez.

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: Doğrulanmış Faturaların Taranması
* **Given**: `invoices` tablosunda `status = EPOD_VERIFIED` olan faturalar varken,
* **When**: Faktoring worker tetiklendiğinde,
* **Then**: Uygun faturalar çekilmeli, %2.5 komisyon kesilerek net hak ediş hesaplanmalıdır.

### AC-2: Ödeme Servisine Emrin İletilmesi
* **Given**: 1000 GBP tutarındaki fatura için 975 GBP net tutar hesaplandığında,
* **When**: `executeInstantPayout(invoiceId)` çağrıldığında,
* **Then**: `PaymentService` gRPC uç noktası tetiklenerek taşımacı IBAN/hesabına transfer emri verilmeli, fatura statüsü `FACTORING_APPROVED` yapılmalıdır.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-payout-worker/src/main/java/com/hozgan/smartpay/payout/
├── service/
│   ├── FactoringCalculationService.java  // %2.5 komisyon ve net tutar hesabı
│   └── PayoutExecutionService.java       // Payment gRPC entegrasyonu
├── worker/
│   └── FactoringPayoutScheduler.java     // Sanal thread'lerle periyodik tarama
└── config/
    └── VirtualThreadExecutorConfig.java  // Project Loom Executor yapılandırması
```
