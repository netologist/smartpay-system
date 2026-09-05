# Sıralama Diyagramları (Sequence Diagrams)

Bu doküman, SmartPay platformundaki uçtan uca kritik iş akışlarının servisler ve veritabanları arasındaki etkileşim sıralamasını gösterir.

---

## 1. Teslimat Kanıtı (ePOD) Doğrulama ve Fatura Üretimi Akışı

Sürücünün teslimatı tamamlamasından faturanın otomatik kesilmesine kadar olan süreç:

```mermaid
sequenceDiagram
    autonumber
    actor Driver as Sürücü / Taşıyıcı
    participant Gateway as API Gateway
    participant InvoiceSvc as smartpay-invoice-service
    participant S3 as AWS S3 Storage
    participant Kafka as Apache Kafka
    participant PayoutWorker as smartpay-payout-worker

    Driver->>S3: Teslimat fotoğrafını yükle
    S3-->>Driver: s3_photo_url dön

    Driver->>Gateway: POST /api/v1/epod/verify (loadId, coords, signatureHash, s3_url)
    Gateway->>InvoiceSvc: İsteği ilet
    
    Note over InvoiceSvc: Koordinat sınırlarını &<br/>SHA-256 imza hash'ini doğrula
    InvoiceSvc->>InvoiceSvc: epod_records tablosuna kaydet (verified=true)
    
    Note over InvoiceSvc: Fiyatlama Motorunu Çalıştır:<br/>Base + %12 Fuel + %20 VAT = Total
    InvoiceSvc->>InvoiceSvc: invoices tablosuna kaydet (status=EPOD_VERIFIED)

    InvoiceSvc->>Kafka: Event yayınla: smartpay.events.invoice (InvoiceIssuedEvent)
    InvoiceSvc-->>Gateway: HTTP 201 Created (InvoicePricing DTO)
    Gateway-->>Driver: Fatura başarıyla oluşturuldu

    Kafka->>PayoutWorker: InvoiceIssuedEvent tüketilir
    Note over PayoutWorker: Faktoring Uygunluk Değerlendirmesi Başlar
```

---

## 2. Erken Ödeme (Factoring Payout) & Ledger Çift Taraflı Kayıt Akışı

Faturanın erken ödenmesi için `payout-worker`'ın `payment-service` ve `ledger-service` ile olan etkileşimi:

```mermaid
sequenceDiagram
    autonumber
    participant Worker as smartpay-payout-worker
    participant PaymentSvc as smartpay-payment-service
    participant LedgerSvc as smartpay-ledger-service
    participant Bank as ClearBank API
    participant Kafka as Apache Kafka

    Note over Worker: Onaylı faturayı hesapla:<br/>Total £1000 - %2.5 Komisyon (£25) = Net £975
    Worker->>PaymentSvc: gRPC: InitiatePayment (CarrierId, £975, EndToEndId)

    Note over PaymentSvc: Idempotency tablosuna<br/>PROCESSING kilidi at
    PaymentSvc->>LedgerSvc: gRPC: HoldFunds (EscrowAccount, £975, EndToEndId)
    
    Note over LedgerSvc: SELECT FOR UPDATE ile bakiye kitle<br/>available_balance >= £975 kontrolü yap<br/>hold_balance_pence artır
    LedgerSvc-->>PaymentSvc: HoldFundsResponse (new_hold_balance)

    PaymentSvc->>PaymentSvc: transactional_outbox tablosuna yaz (PAYMENT_INITIATED)
    PaymentSvc-->>Worker: InitiatePaymentResponse (status=PROCESSING)

    Note over PaymentSvc: Outbox Worker event'i okur (SKIP LOCKED)
    PaymentSvc->>Bank: Faster Payments API çağrısı (£975)
    Bank-->>PaymentSvc: 200 OK (Settled / Transfer Sent)

    PaymentSvc->>LedgerSvc: gRPC: ReleaseHold (capture=true, £975)
    Note over LedgerSvc: Sıfır-Toplamlı Yevmiye Fişi Kes:<br/>DEBIT Escrow £975 | CREDIT Carrier £975<br/>cleared_balance kalıcı düş
    LedgerSvc-->>PaymentSvc: ReleaseHoldResponse (captured=true)

    PaymentSvc->>Kafka: Event yayınla: PaymentSettledEvent
```

---

## 3. Banka Ekstresi (CAMT.053) İçe Aktarma & Otomatik Mutabakat Akışı

Bankadan gelen ekstre satırlarının defter yevmiye kayıtlarıyla eşleştirilmesi:

```mermaid
sequenceDiagram
    autonumber
    actor Ops as Finans Operatörü
    participant Gateway as API Gateway
    participant ReconSvc as smartpay-recon-service
    participant LedgerSvc as smartpay-ledger-service

    Ops->>Gateway: POST /api/v1/recon/statements/upload (CAMT.053 XML Dosyası)
    Gateway->>ReconSvc: İsteği ilet

    Note over ReconSvc: XML Ayrıştırıcı çalışır:<br/>Header ve Statement Lines okunur
    ReconSvc->>ReconSvc: bank_statements & bank_statement_lines kaydet (UNMATCHED)

    loop Her Ekstre Satırı İçin
        ReconSvc->>LedgerSvc: gRPC: GetTransactionByReference (end_to_end_id)
        alt Eşleşen Yevmiye Fişi Bulundu ve Tutar/Para Birimi Eşit
            LedgerSvc-->>ReconSvc: TransactionDetail (amount, currency, status=POSTED)
            ReconSvc->>ReconSvc: Satırı güncelle (reconciliation_status=MATCHED, matched_entry_id)
        else Tutar Uyuşmazlığı Veya Fiş Bulunamadı
            ReconSvc->>ReconSvc: Satırı işaretle (reconciliation_status=DISCREPANCY)
            Note over ReconSvc: Denetim Alert'i Oluştur
        end
    end

    ReconSvc-->>Gateway: Mutabakat Raporu (100 satır: 98 Matched, 2 Discrepancy)
    Gateway-->>Ops: Rapor Görüntülenir
```
