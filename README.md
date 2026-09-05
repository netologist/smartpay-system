# SmartPay: Modern Lojistik Ödeme, Defter-i Kebir & Faktoring Platformu

[![Java 25](https://img.shields.io/badge/Java-25%20(Project%20Loom)-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![gRPC](https://img.shields.io/badge/gRPC-1.70.0-blueviolet.svg)](https://grpc.io/)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-1.4.1-red.svg)](https://www.archunit.org/)

SmartPay; İngiltere ve Avrupa navlun taşımacılığı lojistiği için tasarlanmış, **Java 25 (Virtual Threads)** ve **Spring Boot 4.1** tabanlı, çift taraflı (double-entry) muhasebe, anında taşımacı faktoring hak edişi, elektronik teslimat kanıtı (ePOD) doğrulama ve ISO-20022 banka mutabakatı sağlayan yeni nesil dağıtık finansal ödeme platformudur.

---

## 📑 Dokümantasyon Dizini

Platformun tüm mimari kararları, diyagramları ve geliştirici hikayeleri `docs/` altında detaylandırılmıştır:

* 🏛️ [**C4 Mimari Modelleri (Context, Container, Component, Code)**](docs/architecture/c4-architecture-models.md)
* 🗺️ [**Yüksek Seviye Sistem Mimarisi & Bounded Contexts**](docs/architecture/high-level-architecture.md)
* ⚙️ [**Düşük Seviye Tasarım & Kilit / Sıfır-Toplam Algoritmaları**](docs/architecture/low-level-architecture.md)
* 🔄 [**Sıralama Diyagramları (Sequence Diagrams)**](docs/architecture/sequence-diagrams.md)
* 👥 [**Kullanım Senaryosu Diyagramları (Use Case Diagrams)**](docs/architecture/usecase-diagrams.md)
* 📘 [**gRPC & Protobuf Teknik Kılavuzu**](docs/architecture/grpc-technical-guide.md)
* 📜 [**Mimari Karar Kayıtları (ADR-001 - ADR-007)**](docs/decisions/README.md)
* 📑 [**OpenAPI 3.1 REST Spesifikasyonları**](docs/openapi/README.md)
* 📋 [**Geliştirici Hikaye Kartları (STORY-001 - STORY-006)**](docs/stories/README.md)
* ⚠️ [**Teknik Borç Kayıtları (TD-001: Currency Master)**](docs/tech-debt/TD-001-currency-definitions-master-table.md)

---

## 🌐 Servis Port Haritası (Port Mappings)

| Servis | Modül Adı | HTTP Port | gRPC Port | PostgreSQL Veritabanı | Sorumluluk |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `smartpay-gateway` | `8080` | — | `smartpay_db` | Dış giriş kapısı, ters proxy, JWT auth, SHA-256 Idempotency filtresi |
| **Ledger Service** | `smartpay-ledger-service` | `8081` | `9091` | `smartpay_db` | Çift taraflı yevmiye fişi, bakiye transferi, bloke koyma/çözme |
| **Payment Service** | `smartpay-payment-service` | `8082` | `9092` | `smartpay_db` | Faster Payments / VRP başlatma, Transactional Outbox |
| **Invoice Service** | `smartpay-invoice-service` | `8083` | `9093` | `smartpay_db` | ePOD imza doğrulama, navlun hesaplama (baz + yakıt + KDV) |
| **Recon Service** | `smartpay-recon-service` | `8084` | `9094` | `smartpay_db` | CAMT.053 XML / MT940 banka ekstre mutabakat motoru |
| **Risk Service** | `smartpay-risk-service` | `8085` | `9095` | `smartpay_db` | Dolandırıcılık tespiti ve kredi risk değerlendirmesi |
| **Notification Svc** | `smartpay-notification-service`| `8086` | `9096` | `smartpay_db` | Olay odaklı e-posta / SMS bildirim motoru |
| **Payout Worker** | `smartpay-payout-worker` | `8087` | — | `smartpay_db` | Sanal thread (Virtual Thread) faktoring ödeme arka plan işçisi |
| **PostgreSQL** | `postgres` | `5432` | — | `smartpay_db` | Birincil ilişkisel ACID veritabanı (B-Tree UUIDv7) |
| **Redpanda (Kafka API)** | `redpanda` | `9092` | — | — | Hafif C++20 motorlu, yerel Kafka uyumlu olay veri yolu (ADR-007) |
| **Redpanda Console** | `redpanda-console` | `8090` | — | — | Redpanda topic ve mesaj izleme web paneli (`http://localhost:8090`) |

---

## 🛠️ Yerel Ortam Kurulumu (Step-by-Step Setup)

### 1. Gereksinimler (Prerequisites)
* **Java 25**:
  ```bash
  # SDKMAN ile:
  sdk install java 25-open
  # veya mise ile:
  mise use java@25
  # Kontrol:
  java -version # OpenJDK 25 çıktısını doğrulamalı
  ```
* **Apache Maven 3.9+**:
  ```bash
  mvn -version # Apache Maven 3.9.x çıktısını doğrulamalı
  ```
* **Docker & Docker Compose**:
  ```bash
  docker compose version
  ```

---

### 2. Altyapıyı Ayağa Kaldırma (Docker Compose)
PostgreSQL 16, Redpanda (hafif Kafka motoru) ve Redpanda Console servislerini başlatın:
```bash
docker compose up -d
```

Konteynerlerin sağlık durumunu doğrulayın:
```bash
docker compose ps
```
* **PostgreSQL**: `localhost:5432` (Kullanıcı: `smartpay_admin`, Şifre: `smartpay_secret`, DB: `smartpay_db`)
* **Redpanda (Kafka API)**: `localhost:9092` (Spring Boot doğrudan bağlanır)
* **Redpanda Console Web Paneli**: Tarayıcıda `http://localhost:8090`

---

### 3. gRPC Kontratlarını Üretme & Projeyi Derleme
Öncelikle `smartpay-proto` içindeki `.proto` dosyalarını Java sınıflarına derleyin:
```bash
mvn compile -pl smartpay-proto
```

Ardından tüm projenin bağımlılıklarını ve sınıflarını derleyin:
```bash
mvn test-compile
```

---

### 4. Birim ve Mimari Testleri Çalıştırma (ArchUnit)
`smartpay-common` içindeki para modeli, tip güvenli ID'ler, converter'lar ve ArchUnit mimari kurallarını (Java 25, Records, Virtual Threads güvenliği) test edin:
```bash
mvn clean test -pl smartpay-common
```
*(63 testin tamamı 0 hata ile geçmelidir)*

---

### 5. Mikroservisleri Çalıştırma
Her servis bağımsız olarak Spring Boot Maven eklentisiyle ayağa kaldırılabilir:

```bash
# 1. Defter Servisini Başlatma (HTTP: 8081, gRPC: 9091)
mvn spring-boot:run -pl smartpay-ledger-service

# 2. Fatura & ePOD Servisini Başlatma (HTTP: 8083)
mvn spring-boot:run -pl smartpay-invoice-service

# 3. Ödeme Servisini Başlatma (HTTP: 8082)
mvn spring-boot:run -pl smartpay-payment-service

# 4. API Gateway'i Başlatma (HTTP: 8080)
mvn spring-boot:run -pl smartpay-gateway
```

---

## 🗄️ Veritabanı Şeması & Flyway Göçleri

Veritabanı migration dosyaları mikroservislerin `src/main/resources/db/migration/` dizinlerinde yer alır:

* **`V1__init_accounts_and_balances.sql`** (`smartpay-ledger-service`):
  * `accounts` tablosu (Hesap kartları, para birimi).
  * `account_balances` tablosu (Temiz bakiye, bloke bakiye, `@Version` optimistik kilit).
* **`V2__init_double_entry_ledger.sql`** (`smartpay-ledger-service`):
  * `journal_transactions` tablosu (İşlem fişi başlığı, tekillik anahtarı).
  * `journal_entries` tablosu (Değiştirilemez borç/alacak muhasebe satırları; UPDATE/DELETE trigger korumalı).
* **`V3__init_invoicing_and_epod.sql`** (`smartpay-invoice-service`):
  * `epod_records` tablosu (ePOD GPS koordinatları, S3 fotoğrafı, SHA-256 dijital imza).
  * `invoices` tablosu (Navlun faturası, baz tutar, %12 yakıt sürşarjı, %20 KDV, araç tipi).
* **`V4__init_transactional_outbox.sql`** (`smartpay-payment-service`):
  * `transactional_outbox` tablosu (Kafka için `SKIP LOCKED` indeksli At-Least-Once olay deposu).
* **`V5__init_idempotency_records.sql`** (`smartpay-payment-service` & `smartpay-gateway`):
  * `idempotency_records` tablosu (SHA-256 gövde hash'li iki katmanlı dağıtık kilit).
* **`V6__init_bank_reconciliation.sql`** (`smartpay-recon-service`):
  * `bank_statements` & `bank_statement_lines` (CAMT.053 / MT940 ekstre satırları).

---

## 🚀 Geliştirici Yol Haritası (Story-by-Story)

Platformu geliştirmeye başlamak için aşağıdaki hikaye kartlarını sırayla takip ediniz:

1. [**STORY-001: Double-Entry Ledger & Atomik Transfer Motoru**](docs/stories/STORY-001-ledger-double-entry-engine.md) *(Başlangıç noktası)*
2. [**STORY-002: Navlun Faturası & ePOD Fiyatlandırma Motoru**](docs/stories/STORY-002-invoice-epod-pricing-engine.md)
3. [**STORY-003: Ödeme Başlatma & Transactional Outbox**](docs/stories/STORY-003-payment-initiation-outbox.md)
4. [**STORY-004: Taşımacı Faktoring & Erken Ödeme Worker'ı**](docs/stories/STORY-004-payout-factoring-worker.md)
5. [**STORY-005: Banka Ekstresi & Otomatik Mutabakat Motoru**](docs/stories/STORY-005-bank-reconciliation-engine.md)
6. [**STORY-006: API Gateway & Dağıtık Idempotency Filtresi**](docs/stories/STORY-006-api-gateway-idempotency.md)

gRPC servislerini yazarken takıldığınız konularda [**gRPC Teknik Rehberi**](docs/architecture/grpc-technical-guide.md)'ne başvurabilirsiniz.
