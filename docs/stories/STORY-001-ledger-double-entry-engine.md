# STORY-001: Double-Entry Ledger (Defter-i Kebir) & Bakiye Transfer Motoru

## 📌 Genel Bakış
* **Hedef Modül**: `smartpay-ledger-service`
* **Öncelik**: P0 (Temel Motor / Core)
* **İlişkili Veritabanı Tabloları**: `accounts`, `account_balances`, `journal_transactions`, `journal_entries` (`V1`, `V2`)
* **İlişkili gRPC Kontratı**: `smartpay-proto/src/main/proto/ledger.proto`
* **Kullanılacak `smartpay-common` Bileşenleri**:
  * `Money` (Aritmetik işlemler ve para yönetimi)
  * `AccountId`, `TransactionId`, `IdempotencyKey` (Tip güvenli ID'ler)
  * `EntryType` (`DEBIT`, `CREDIT`), `JournalStatus` (`POSTED`, `REVERSED`)
  * `InsufficientFundsException`, `UnbalancedJournalTransactionException`, `CurrencyMismatchException`, `AccountNotFoundException`
  * `LedgerTransactionPostedEvent`

---

## 🎯 Kullanıcı Hikayesi
> **Bir** Finansal Ödeme Motoru olarak,  
> **Platform hesapları arasında** çift taraflı (double-entry), sıfır toplamlı ve atomik muhasebe hareketleri gerçekleştirebilmek istiyorum,  
> **Böylece** sistemde bakiye kaçakları, race condition kaynaklı negatif bakiye oluşumu engellensin ve tam bir denetim izi (audit trail) sağlansın.

---

## 📐 Mimari ve Domain Kuralları

1. **Sıfır Toplamlı Yevmiye Fişi Değişmezi (Zero-Sum Invariant)**:
   Her işlem fişinde (`journal_transactions`) yer alan tüm satırların (`journal_entries`) borç toplamı ile alacak toplamı birbirine tam eşit olmalıdır:
   $$\sum \text{Debit} = \sum \text{Credit}$$
   Eşitsizlik durumunda işlem veritabanına asla yazılmamalı, `UnbalancedJournalTransactionException` fırlatılmalıdır.

2. **Değiştirilemezlik (Append-Only Ledger)**:
   Muhasebe satırları asla güncellenemez (`UPDATE`) veya silinemez (`DELETE`). Veritabanındaki `trg_prevent_ledger_modification` trigger'ı bunu veritabanı seviyesinde de korur.

3. **Pessimistic Locking & Bakiye Güvencesi**:
   Bakiye transferinde iki hesap eşzamanlı olarak kilitlenmelidir:
   * Kilitlenme sırası deadlock'ları önlemek için daima `AccountId`'lerin doğal sıralamasına (lexicographical order) göre yapılmalıdır (`id1.compareTo(id2) < 0 ? lock(id1), lock(id2) : lock(id2), lock(id1)`).
   * Kullanılabilir bakiye: `available = clearedBalance - holdBalance`. Yetersiz bakiye durumunda `InsufficientFundsException` fırlatılmalıdır.

4. **Idempotency**:
   Aynı `idempotency_key` ile gelen isteklerde işlem tekrar çalıştırılmamalı, mevcut işlem kaydı geri dönülmelidir.

---

## ✅ Kabul Kriterleri (Acceptance Criteria)

### AC-1: Sıfır Toplamlı Fiş Doğrulaması
* **Given**: 200 GBP DEBIT ve 200 GBP CREDIT içeren geçerli bir işlem fişi isteği geldiğinde,
* **When**: Fiş kaydedildiğinde,
* **Then**: `journal_transactions` tablosuna `POSTED` statüsünde yeni kayıt (`UUIDv7`), `journal_entries` tablosuna 2 satır yazılmalı ve `TransactionId` dönmelidir.
* **And**: Borç ve alacak toplamı eşit değilse (örn. 200 GBP DEBIT vs 190 GBP CREDIT), `UnbalancedJournalTransactionException` fırlatılmalı ve rollback yapılmalıdır.

### AC-2: Atomik Para Transferi (TransferFunds)
* **Given**: Kaynak hesapta 500 GBP bakiye, hedef hesapta 100 GBP bakiye varken,
* **When**: 200 GBP transfer isteği geldiğinde,
* **Then**: Kaynak hesap 300 GBP'ye düşmeli, hedef hesap 300 GBP'ye çıkmalı, muhasebe fişi (Kaynak: DEBIT 200 GBP, Hedef: CREDIT 200 GBP) otomatik yazılmalıdır.
* **And**: Kaynak bakiye yetersizse bakiye düşümü yapılmamalı, `InsufficientFundsException` fırlatılmalıdır.

### AC-3: Bakiye Bloke Koyma ve Çözme (Hold & Release/Capture)
* **Given**: Hesabında 1000 GBP temiz (cleared) bakiye olan bir taşıyıcı için,
* **When**: `holdFunds(accountId, 300 GBP, refId)` çağrıldığında,
* **Then**: `hold_balance_pence` 300 GBP artmalı, `available` bakiye 700 GBP'ye düşmelidir.
* **When**: `releaseHold(..., capture = true)` çağrıldığında:
  * `hold_balance_pence` 300 GBP azalmalı, `cleared_balance_pence` 300 GBP azalmalıdır (para hesaptan tamamen tahsil edilir).
* **When**: `releaseHold(..., capture = false)` çağrıldığında:
  * `hold_balance_pence` 300 GBP azalır, `cleared` bakiye değişmez; kullanılabilir bakiye tekrar 1000 GBP olur (bloke iptal).

### AC-4: gRPC Uç Noktası
* **Given**: `smartpay-proto` içindeki `LedgerServiceGrpc.LedgerServiceImplBase`,
* **When**: `GetBalance`, `TransferFunds`, `HoldFunds` çağrıları yapıldığında,
* **Then**: Domain servisleri tetiklenmeli, gRPC `MoneyProto` formatında yanıt dönmelidir.

---

## 💻 Geliştirilecek Sınıflar Rehberi

```
smartpay-ledger-service/src/main/java/com/hozgan/smartpay/ledger/
├── service/
│   ├── AccountBalanceService.java      // Bakiye okuma, lock'lı güncelleme, hold/release
│   ├── LedgerDomainService.java        // Çift taraflı fiş oluşturma ve sıfır-toplam denetimi
│   └── impl/
│       ├── AccountBalanceServiceImpl.java
│       └── LedgerDomainServiceImpl.java
├── grpc/
│   ├── LedgerGrpcService.java          // LedgerServiceGrpc.LedgerServiceImplBase extend eder
│   └── mapper/
│       └── LedgerProtoMapper.java      // Money <-> MoneyProto, AccountBalance <-> GetBalanceResponse
└── config/
    └── GrpcServerConfig.java           // gRPC server Netty port/lifecycle yapılandırması
```

### Örnek Bakiye Servisi Metot İmzaları:
```java
public interface AccountBalanceService {
    AccountBalance getBalance(AccountId accountId);
    AccountBalance holdFunds(AccountId accountId, Money amount, String referenceId, IdempotencyKey key);
    AccountBalance releaseHold(AccountId accountId, String holdId, Money amount, boolean capture);
    void transfer(AccountId source, AccountId target, Money amount, String reference, IdempotencyKey key);
}
```
