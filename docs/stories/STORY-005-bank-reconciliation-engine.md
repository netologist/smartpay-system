# STORY-005: Banka Ekstresi & Otomatik Mutabakat Motoru

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-recon-service`
* **Öncelik**: P2 (Mutabakat & Finansal Raporlama)
* **İlişkili Veritabanı Tabloları**: `bank_statements`, `bank_statement_lines` (`V6`)
* **İlişkili Servisler**: `smartpay-ledger-service`
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `Money` (Ekstre bakiyeleri ve işlem tutarları)
  * `StatementReference`, `EndToEndId`
  * `EntryType`, `ReconciliationStatus` (`UNMATCHED`, `MATCHED`, `MANUALLY_ADJUSTED`)
  * `UnmatchedBankStatementException`

---

## 🎯 Kullanıcı Hikayesi
> **Bir** Finans Operasyon Yöneticisi olarak,  
> **ClearBank / Barclays gibi bankalardan gelen CAMT.053 XML veya MT940 ekstrelerini içeri aktarmak**, her satırı `end_to_end_id` referansıyla muhasebe defterindeki yevmiye kayıtlarıyla otomatik eşleştirmek istiyorum,  
> **Böylece** banka bakiyesi ile defter bakiyesi arasındaki farklar (discrepancy) dakikalar içinde tespit edilsin.

---

## 📐 Mimari ve Mutabakat Kuralları

1. **Eşleştirme Anahtarı (Matching Key)**:
   * Faster Payments / SEPA transferlerinde banka satırındaki `EndToEndId`, ödeme başlatılırken üretilen referanstır.
   * `bank_statement_lines.end_to_end_id == journal_transactions.idempotency_key` veya `reference_id`.

2. **Tutar ve Yön Uyumu Değişmezi**:
   * Bankada `CREDIT` (para girişi), defterde de hesaba `CREDIT` olmalıdır.
   * Tutar kuruşu kuruşuna eşit olmalıdır (`bankAmount.equals(ledgerAmount)`).

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: CAMT.053 Ekstre Ayrıştırma ve Kaydı
* **Given**: Bankadan gelen 100 satırlık CAMT.053 XML dosyası yüklendiğinde,
* **When**: `importStatement(xmlStream)` çağrıldığında,
* **Then**: `bank_statements` ve `bank_statement_lines` tablolarına `reconciliation_status = UNMATCHED` olarak kaydedilmelidir.

### AC-2: Otomatik Eşleştirme (Auto-Reconciliation)
* **Given**: 500 GBP tutarında ve `E2E-998822` referanslı bir banka satırı varken,
* **When**: Mutabakat motoru çalıştığında,
* **Then**: Ledger servisinden bu referansa ait fiş aranmalı; tutar ve yön tutuyorsa satır `MATCHED` statüsüne çekilmeli ve `matched_entry_id` atanmalıdır.

### AC-3: Uyuşmazlık Tespiti (Discrepancy Reporting)
* **Given**: Referansı bulunan fakat tutarı bankada 500 GBP, defterde 490 GBP olan bir işlemde,
* **When**: Eşleştirme yapıldığında,
* **Then**: Otomatik eşleşme yapılmamalı, satır `MANUALLY_ADJUSTED` veya `UNMATCHED` olarak işaretlenmeli ve alert üretilmelidir.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-recon-service/src/main/java/com/hozgan/smartpay/recon/
├── parser/
│   ├── StatementParser.java            // CAMT.053 XML / MT940 ayrıştırıcı arayüzü
│   └── impl/
│       └── Camt053XmlStatementParser.java
├── service/
│   ├── BankStatementService.java       // Ekstre yükleme ve sorgulama
│   └── ReconciliationEngine.java       // E2E ID matching algoritması
└── web/
    └── ReconciliationController.java   // POST /api/v1/recon/statements/upload
```
