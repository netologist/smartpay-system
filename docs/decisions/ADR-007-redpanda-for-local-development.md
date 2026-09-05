# ADR-007: Yerel Geliştirme ve Testlerde Redpanda (Kafka Uyumlu) Kullanımı

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
SmartPay platformu mikroservisler arası asenkron olay iletimi (Event-Driven Architecture) ve Transactional Outbox deseni için Apache Kafka protokolünü kullanmaktadır. Ancak yerel geliştirme (Local Dev) ortamında klasik Apache Kafka'yı Docker üzerinden çalıştırmak:
1. **Yüksek Bellek Tüketimi**: Apache Kafka JVM tabanlıdır ve broker başına ~1.5 GB – 2 GB RAM tüketir. Geliştiricinin makinesinde 10 mikroservis, PostgreSQL ve Kafka aynı anda çalışırken ciddi kaynak darboğazı yaşanır.
2. **Yavaş Başlatma Süresi**: ZooKeeper veya KRaft metadata eşitlemesi nedeniyle konteynerin hazır hale gelmesi 20-30 saniye sürer.
3. **Apple Silicon & ARM64 Uyumluluğu**: M1/M2/M3 işlemcilerde x86 emülasyonu veya JVM konteyner yükü ekstra pil ve CPU harcar.

## Karar
Yerel geliştirme ortamında (`docker-compose.yml`) ve entegrasyon testlerinde Apache Kafka yerine **Redpanda** kullanılmasına karar verilmiştir:
1. **C++20 & Seastar Mimarisi**: Redpanda, thread-per-core mimarisiyle sıfır JVM/Garbage Collection yüküyle çalışır.
2. **Çok Düşük Kaynak Tüketimi**: Yalnızca ~150 MB – 300 MB RAM tüketir ve <1 saniyede tam sağlıklı (healthy) duruma gelir.
3. **%100 Kafka API Uyumluluğu (Drop-in Replacement)**: Uygulama kodunda (Spring Boot) hiçbir değişiklik gerekmez. Standart `spring-kafka` kütüphanesi doğrudan `localhost:9092` portuna bağlanır.
4. **Redpanda Console**: Yerleşik şık web arayüzü (`localhost:8090`) ile topic'ler, consumer group'lar ve partition'lar anında görselleştirilir.

## Üretim (Production) Uyumu
Uygulama kodları hiçbir Redpanda'ya özel kütüphane içermez. Yalnızca standart Kafka API'si (`spring-kafka`, `ProducerRecord`, `KafkaListener`) kullanılır. Bu sayede üretim ortamında (Kubernetes) tercihe göre:
* AWS MSK (Managed Streaming for Kafka),
* Strimzi Apache Kafka Operator,
* Confluent Cloud veya Redpanda Cloud

hiçbir kod değişikliği yapılmadan yalnızca `spring.kafka.bootstrap-servers` ortam değişkeniyle değiştirilebilir.

## Sonuçlar
* **Olumlu**: 
  * Yerel ortamda $10\times$ daha düşük RAM tüketimi ve anında açılış.
  * Apple Silicon (M serisi) işlemcilerde yerel ARM64 hızı.
  * Kod seviyesinde %100 standart Kafka kalması sayesinde üretim bağımsızlığı.
* **Olumsuz**:
  * Ekip üyelerinin Redpanda'nın yerel bir Kafka alternatifi olduğunu bilmesi gerekir.
