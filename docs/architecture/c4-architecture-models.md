# C4 Mimari Modelleri (C4 Architecture Models)

Bu doküman, SmartPay Lojistik Ödeme ve Fintek Platformu'nun **C4 Model** (Context, Container, Component, Code) standardına göre katmanlı mimari diyagramlarını içerir.

---

## 🏛️ Seviye 1: Sistem Bağlam Diyagramı (System Context Diagram)

SmartPay platformunun dış dünya aktörleri ve entegre olduğu harici finansal/lojistik sistemlerle ilişkisini gösterir.

```mermaid
C4Context
    title Sistem Bağlam Diyagramı (System Context) - SmartPay Platformu

    Person(shipper, "Yük Veren (Shipper)", "Navlun siparişi veren ve fatura ödemelerini gerçekleştiren kurumsal müşteri.")
    Person(carrier, "Taşıyıcı / Sürücü (Carrier)", "Yükü taşıyan, teslimat kanıtı (ePOD) yükleyen ve erken ödeme (factoring) alan lojistik aktör.")
    Person(financeOps, "Finans Operatörü", "Banka mutabakatlarını denetleyen ve şüpheli işlemleri yöneten şirket içi yetkili.")

    Enterprise_Boundary(b0, "SmartPay Logistics Payment Platform") {
        System(smartpay, "SmartPay Core Platform", "Çift taraflı defter, lojistik faturalandırma, faktoring hak ediş ve banka mutabakatını yöneten dağıtık sistem.")
    }

    System_Ext(bankRails, "Banka & Takas Sistemleri", "ClearBank, Barclays, Modulr (Faster Payments, BACS, Open Banking VRP API'leri).")
    System_Ext(s3Storage, "AWS S3 / MinIO", "ePOD teslimat fotoğrafları ve dijital imza kanıtlarının saklandığı nesne deposu.")
    System_Ext(notificationProvider, "SMS / Email Gateway", "Twilio / SendGrid teslimat ve ödeme bildirim sağlayıcıları.")

    Rel(shipper, smartpay, "Fatura görüntüler, ödeme emri verir", "HTTPS / REST")
    Rel(carrier, smartpay, "ePOD teslimat kanıtı yükler, erken ödeme talep eder", "HTTPS / Mobile App")
    Rel(financeOps, smartpay, "Ekstre yükler, mutabakat ve defter raporlarını izler", "HTTPS / Web UI")

    Rel(smartpay, bankRails, "Ödeme emri iletir, CAMT.053 ekstre çeker", "mTLS / REST / ISO-20022")
    Rel(smartpay, s3Storage, "Teslimat fotoğraflarını arşivler", "S3 API / IAM")
    Rel(smartpay, notificationProvider, "Ödeme ve fatura bildirimlerini iletir", "REST / Webhooks")
```

---

## 📦 Seviye 2: Konteyner Diyagramı (Container Diagram)

SmartPay platformunu oluşturan mikroservisleri, veri depolarını ve servisler arası iletişim protokollerini (gRPC, Kafka, REST) gösterir.

```mermaid
C4Container
    title Konteyner Diyagramı (Container Diagram) - SmartPay Platformu

    Person(client, "İstemciler", "Web UI / Mobil Uygulamalar")

    Container(gateway, "API Gateway", "Spring Boot 4.1 / Java 25", "Ters proxy, JWT doğrulama, rate limiting ve iki katmanlı SHA-256 Idempotency filtresi.")

    Container_Boundary(microservices, "Mikroservis Ekosistemi") {
        Container(invoiceService, "Invoice Service", "Spring Boot / Java 25", "ePOD imza doğrulama, navlun fiyatlama (baz + yakıt + KDV), fatura yaşam döngüsü.")
        Container(ledgerService, "Ledger Service", "Spring Boot / Java 25", "Çift taraflı defter-i kebir, sıfır toplam değişmezi, bakiye bloke koyma/çözme, transfer motoru.")
        Container(paymentService, "Payment Service", "Spring Boot / Java 25", "Ödeme başlatma, Faster Payments/VRP orkestrasyonu, Transactional Outbox.")
        Container(payoutWorker, "Payout Worker", "Spring Boot / Virtual Threads", "Faktoring faturalarını tarayan ve anında erken ödeme çıkaran sanal thread arka plan işçisi.")
        Container(reconService, "Reconciliation Service", "Spring Boot / Java 25", "CAMT.053 XML / MT940 banka ekstre ayrıştırma ve end_to_end_id defter mutabakatı.")
        Container(riskService, "Risk Service", "Spring Boot / Java 25", "Taşıyıcı ve yük veren kredi skorlaması ve dolandırıcılık tespiti.")
        Container(notificationService, "Notification Service", "Spring Boot / Java 25", "Olay odaklı SMS/E-posta bildirim gönderimi.")
    }

    ContainerDb(ledgerDb, "Ledger DB", "PostgreSQL 16", "accounts, account_balances, journal_transactions, journal_entries")
    ContainerDb(invoiceDb, "Invoice DB", "PostgreSQL 16", "epod_records, invoices")
    ContainerDb(paymentDb, "Payment DB", "PostgreSQL 16", "transactional_outbox, idempotency_records")
    ContainerDb(reconDb, "Recon DB", "PostgreSQL 16", "bank_statements, bank_statement_lines")
    ContainerQueue(kafka, "Apache Kafka", "Message Broker", "smartpay.events.* (epod-verified, invoice-issued, payment-settled, ledger-posted)")

    Rel(client, gateway, "API istekleri", "HTTPS / JSON")
    Rel(gateway, invoiceService, "Fatura & ePOD çağrıları", "HTTP / REST")
    Rel(gateway, paymentService, "Ödeme emirleri", "HTTP / REST")

    Rel(invoiceService, kafka, "EpodVerified, InvoiceIssued", "Kafka Producer")
    Rel(payoutWorker, invoiceService, "Onaylı faturaları sorgular", "REST / gRPC")
    Rel(payoutWorker, paymentService, "Erken ödeme başlatır", "gRPC over HTTP/2")

    Rel(paymentService, ledgerService, "HoldFunds, TransferFunds", "gRPC over HTTP/2 (smartpay-proto)")
    Rel(paymentService, kafka, "Outbox Worker ile event yayınlar", "Kafka Producer")

    Rel(reconService, ledgerService, "İşlem referansı doğrular", "gRPC over HTTP/2")

    Rel(ledgerService, ledgerDb, "Veri okuma/yazma (Pessimistic Lock)", "JDBC / HikariCP")
    Rel(invoiceService, invoiceDb, "Fatura ve ePOD kaydı", "JDBC / HikariCP")
    Rel(paymentService, paymentDb, "Outbox ve Idempotency kaydı", "JDBC / HikariCP")
    Rel(reconService, reconDb, "Ekstre kaydı ve mutabakat", "JDBC / HikariCP")
```

---

## 🧩 Seviye 3: Bileşen Diyagramı (Component Diagram - Ledger Service)

Platformun en kritik bileşeni olan `smartpay-ledger-service` modülünün iç mimari yapısını ve katmanlarını gösterir.

```mermaid
C4Component
    title Bileşen Diyagramı (Component Diagram) - smartpay-ledger-service

    Container_Boundary(ledgerBoundary, "smartpay-ledger-service") {
        Component(ledgerGrpc, "LedgerGrpcService", "gRPC Controller", "smartpay-proto LedgerServiceImplBase implementasyonu. GetBalance, TransferFunds, HoldFunds endpoint'leri.")
        Component(protoMapper, "LedgerProtoMapper", "Mapper", "Protobuf MoneyProto <-> smartpay-common Money çevrimi.")
        Component(balanceService, "AccountBalanceService", "Domain Service", "Pessimistic Lock ile bakiye düşümü, hold/release yönetimi, negatif bakiye koruması.")
        Component(ledgerEngine, "LedgerDomainService", "Domain Service", "Çift taraflı yevmiye fişi doğrulama (SUM(DEBIT) == SUM(CREDIT)) ve append-only kayıt.")
        Component(balanceRepo, "AccountBalanceRepository", "Spring Data JPA", "PESSIMISTIC_WRITE lock destekli bakiye okuma ve optimistik version kontrolü.")
        Component(accountRepo, "AccountRepository", "Spring Data JPA", "Hesap kartları CRUD.")
        Component(txRepo, "JournalTransactionRepository", "Spring Data JPA", "Yevmiye fişi başlıkları ve tekillik.")
        Component(entryRepo, "JournalEntryRepository", "Spring Data JPA", "Değiştirilemez borç/alacak muhasebe satırları.")
    }

    Rel(ledgerGrpc, protoMapper, "DTO / Proto dönüşümü", "Java in-process")
    Rel(ledgerGrpc, balanceService, "İş kurallarını tetikler", "Java in-process")
    Rel(ledgerGrpc, ledgerEngine, "Fiş kaydını tetikler", "Java in-process")

    Rel(balanceService, balanceRepo, "SELECT ... FOR UPDATE", "JPA")
    Rel(balanceService, ledgerEngine, "Bakiye transferi için fiş keser", "Java in-process")

    Rel(ledgerEngine, txRepo, "Fiş başlığı yazar", "JPA")
    Rel(ledgerEngine, entryRepo, "Append-only satırlar yazar", "JPA")
```

---

## 💻 Seviye 4: Kod / Sınıf Diyagramı (Code Diagram - Domain Core)

`smartpay-common` içindeki çekirdek domain modellerinin nesne yönelimli ve record temelli tasarımını gösterir.

```mermaid
classDiagram
    direction TB

    class EntityId~T~ {
        <<interface>>
        +value() T
        +asString() String
    }

    class AccountId {
        <<record>>
        -UUID value
        +generate() AccountId$
        +of(UUID) AccountId$
        +of(String) AccountId$
    }

    class Money {
        <<record>>
        -BigDecimal amount
        -Currency currency
        +plus(Money) Money
        +minus(Money) Money
        +times(BigDecimal) Money
        +divide(BigDecimal) Money
        +percent(BigDecimal) Money
        +toMinorUnits() long
        +atLeast(Money) boolean
        +atMost(Money) boolean
        +isZero() boolean
        +isPositive() boolean
    }

    class AccountBalance {
        <<record>>
        -AccountId accountId
        -Money clearedBalance
        -Money holdBalance
        -long version
        +availableBalance() Money
        +canCover(Money) boolean
    }

    class SmartpayDomainException {
        <<abstract sealed>>
        -String errorCode
        -Instant timestamp
        +errorCode() String
        +timestamp() Instant
    }

    class InsufficientFundsException {
        <<final>>
        -AccountId accountId
        -Money requestedAmount
        -Money availableBalance
    }

    class UnbalancedJournalTransactionException {
        <<final>>
        -Money totalDebit
        -Money totalCredit
    }

    EntityId <|.. AccountId : implements
    AccountBalance --> AccountId : belongs to
    AccountBalance --> Money : cleared & hold
    SmartpayDomainException <|-- InsufficientFundsException : permits
    SmartpayDomainException <|-- UnbalancedJournalTransactionException : permits
```
