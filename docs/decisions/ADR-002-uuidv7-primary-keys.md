# ADR-002: Birincil Anahtarlar İçin UUIDv7 (RFC 9562) Tercihi

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
Dağıtık mikroservis mimarisinde PostgreSQL tablolarında birincil anahtar (Primary Key) seçimi kritik bir performans ve tasarım unsurudur:
* Sıralı `BIGINT` (Sequence/Serial): Mikroservisler arası merkezi veritabanı bağımlılığı yaratır ve tahmin edilebilir olduğu için güvenlik açığı teşkil eder.
* `UUIDv4`: Tamamen rastgele olduğu için PostgreSQL B-Tree indeksinde sayfa bölünmelerine (page splitting), aşırı disk I/O'suna ve WAL şişmesine yol açar.
* `ULID`: Sıralıdır ancak PostgreSQL'de yerel bir tip değildir; `VARCHAR(26)` gerektirir ve string indeksleme binary indekslemeye göre daha yavaştır.

## Karar
Tüm veritabanı birincil anahtarlarında IETF tarafından standartlaştırılan **UUIDv7 (RFC 9562)** kullanılmasına karar verilmiştir.

Teknik tasarım:
* İlk 48 bit: Milisaniye cinsinden Unix Epoch zaman damgası.
* Kalan 74 bit: Sürüm (0111), varyant (10) ve kriptografik rastgelelik.
* `smartpay-common` modülü içine sıfır harici bağımlılıkla yüksek performanslı `UuidV7` üreteci eklenmiştir.

## Sonuçlar
* **Olumlu**:
  * PostgreSQL'in yerel 16-bayt `uuid` veri tipiyle tam uyumluluk (sıfır depolama kaybı).
  * Monotonik artan zaman sıralaması sayesinde B-Tree indeksinde page split ve WAL yazımı minimuma iner (`BIGINT` performansı).
  * Dağıtık ortamda merkezi veritabanı sormadan saniyede milyonlarca tekil ID üretilebilir.
  * ID'nin kendisinden işlemin tam oluşturulma zamanı (`extractTimestamp`) doğrudan okunabilir.
* **Olumsuz**:
  * ID'ler zamana göre sıralı olduğu için oluşturulma zamanı gizlenemez (finansal denetim için bu aslında bir avantajdır).
