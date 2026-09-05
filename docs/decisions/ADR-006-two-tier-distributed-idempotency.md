# ADR-006: SHA-256 İstek Parmak İzi ile İki Katmanlı Dağıtık Idempotency

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
Finansal API'lerde istemciler ağ kesintisi, zaman aşımı (timeout) veya mobil bağlantı kopması nedeniyle aynı ödeme veya fatura isteğini birden fazla kez yeniden denerler (retry). Eğer sistem idempotent değilse:
* Aynı fatura için taşımacıya iki kez ödeme çıkabilir (çift ödeme felaketi).
* Kötü niyetli aktörler aynı `Idempotency-Key` başlığıyla farklı tutarlar göndererek sistemi manipüle edebilir.

## Karar
Sistemin tüm mutasyon (POST / PUT) uç noktalarında **İki Katmanlı Dağıtık Idempotency (Two-Tier Idempotency)** uygulanmasına karar verilmiştir:
1. **Katman 1: İstek Parmak İzi (SHA-256 Fingerprinting)**:
   Gelen HTTP gövdesinin kriptografik SHA-256 özeti çıkarılır (`request_hash`).
2. **Katman 2: Veritabanı Kompozit Kilidi (`tenant_id`, `idempotency_key`)**:
   `idempotency_records` tablosuna `status = 'PROCESSING'` olarak ilk satır yazılır.
   * Eğer satır zaten varsa ve durum `PROCESSING` ise: `IdempotencyConflictException` (HTTP 409).
   * Eğer satır var ama gelen `request_hash` kayıtlı olandan farklıysa: `RequestHashMismatchException` (HTTP 422 - Güvenlik ihlali).
   * Eğer satır var ve durum `COMPLETED` ise: İş mantığı tekrar çalıştırılmaz, kaydedilmiş olan `response_code` ve `response_body` anında dönülür (`X-Cache: IDEMPOTENT-HIT`).
3. **TTL ve Süre Aşımı (`expires_at`)**:
   İdempotency anahtarları 24 saat geçerlidir; arka plan job'ı süresi dolan kayıtları temizler.

## Sonuçlar
* **Olumlu**: Sıfır çift çekim/çift ödeme riski, kurcalamaya karşı kriptografik gövde doğrulaması, hızlı önbellek yanıtları.
* **Olumsuz**: Her mutasyon isteğinde ek bir veritabanı okuma/yazma işlemi yapılır (ancak finansal güvenlik için bu ihmal edilebilir bir maliyettir).
