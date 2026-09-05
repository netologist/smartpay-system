# ADR-005: Dağıtık Olaylar İçin PostgreSQL SKIP LOCKED Transactional Outbox

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
Mikroservis mimarisinde en sık karşılaşılan tuzaklardan biri "Çift Yazma (Dual-Write)" problemidir:
```java
// TEHLİKELİ ANTIPATTERN:
paymentRepository.save(payment); // Veritabanına yazıldı
kafkaTemplate.send("payments", event); // Ağ koptu, Kafka çöktü -> EVENT KAYBOLDU!
```
Veritabanı transaction'ı commit edilirken aynı anda harici bir mesaj kuyruğuna (Kafka) güvenle yazmak dağıtık iki aşamalı commit (2PC / XA) gerektirir; bu da sistemi yavaşlatır ve kırılganlaştırır.

## Karar
Tüm asenkron olay yayınlama süreçlerinde **Transactional Outbox Deseni** kullanılmasına karar verilmiştir:
1. **Outbox Tablosu (`transactional_outbox`)**: İş mantığı transaction'ı ile aynı veritabanı oturumunda olay satırı (`JSONB` payload) kaydedilir. ACID garantisiyle olay kesinlikle veritabanında saklanır.
2. **PostgreSQL SKIP LOCKED Polling**:
   ```sql
   SELECT * FROM transactional_outbox
   WHERE processed_at IS NULL
   ORDER BY created_at ASC
   LIMIT 50
   FOR UPDATE SKIP LOCKED;
   ```
   Bu sorgu sayesinde birden fazla sanal thread veya worker instance'ı birbirini bloklamadan saniyede binlerce eventi kuyruğa aktarır.
3. **At-Least-Once Delivery**: Mesaj Kafka'ya teslim edildikten sonra `processed_at = NOW()` olarak güncellenir.

## Sonuçlar
* **Olumlu**: Sıfır veri kaybı garantisi, Kafka arızalarında sistemin çalışmaya devam edebilmesi, yüksek eşzamanlı polling performansı.
* **Olumsuz**: Mesajlar tüketiciler tarafından idempotent işlenmelidir (çünkü ağ hatalarında aynı mesaj birden fazla iletilebilir).
