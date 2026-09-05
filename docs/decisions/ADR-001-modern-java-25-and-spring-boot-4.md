# ADR-001: Modern Java 25 & Spring Boot 4.1 Seçimi

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
SmartPay, mikro-saniye hassasiyetinde bakiye hareketleri, yüksek hacimli eşzamanlı ödemeler ve finansal doğruluk gerektiren bir lojistik ödeme platformudur. Geleneksel JVM mimarilerinde OS thread başına 1MB stack maliyeti, bloklayıcı I/O çağrılarında (veritabanı, gRPC, banka API'leri) thread havuzlarının hızla tükenmesine (thread starvation) yol açmaktadır. Ayrıca mutable (değiştirilebilir) sınıflar çok iş parçacıklı ortamlarda race condition riskini artırır.

## Karar
Sistemin tüm servislerinde **Java 25** ve **Spring Boot 4.1** kullanılmasına karar verilmiştir:
1. **Virtual Threads (Project Loom)**: İşletim sistemi thread'lerine bağlanmadan, milyonlarca eşzamanlı sanal thread ile asenkron karmaşasına girmeden blocking I/O yürütme.
2. **Records & Immutability**: Domain modellerinin ve DTO'ların saf `record` olarak tanımlanmasıyla thread-safety ve sıfır boilerplate sağlanması.
3. **Sealed Types & Pattern Matching**: Domain hatalarının derleme zamanında eksiksiz (`switch` exhaustiveness) yönetilmesi.
4. **Spring Boot 4.1**: Sanal thread'leri yerel olarak destekleyen, Jakarta EE 11 ve modern Java derleyicisi ile tam optimize çalışan uygulama çatısı.

## Alternatifler
* **Java 17 / 21**: Virtual Threads erken aşamadaydı, Java 25 ile üretim olgunluğuna ulaştı.
* **Go / Rust**: Yüksek performans sunsalar da zengin Java kurumsal bankacılık kütüphanesi (ISO-20022 parser'lar, Moneta JSR-354, Hibernate, ArchUnit) eksiktir.

## Sonuçlar
* **Olumlu**: Yüksek işlem kapasitesi (throughput), daha düşük bellek ayak izi, immutable domain modelleri sayesinde güvenli paralellik.
* **Olumsuz**: Bazı eski kütüphaneler (`synchronized` blokları carrier thread pin edenler) elenmek zorunda kalındı; ArchUnit mimari testleri ile bu kurallar otomatik denetlenmektedir.
