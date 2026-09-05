# ADR-004: İç Servisler Arası İletişimde gRPC (HTTP/2) Kullanımı

## Durum
**KABUL EDİLDİ**

## Tarih
2026-09-05

## Bağlam
SmartPay mikroservisleri arasında (örneğin `payment-service` ile `ledger-service`, veya `payout-worker` ile `payment-service`) yoğun, düşük gecikmeli ve tip güvenli senkron RPC çağrılarına ihtiyaç duyulmaktadır. Klasik REST (JSON over HTTP/1.1):
* Her istekte yeni TCP/TLS el sıkışması yapar.
* Metin tabanlı JSON serileştirmesi yüksek CPU ve bant genişliği tüketir.
* DTO değişikliklerinde çalışma zamanında (runtime) sessiz hatalara neden olabilir.

## Karar
İç servisler arası senkron iletişimde **gRPC (Protocol Buffers over HTTP/2)** kullanılmasına karar verilmiştir:
1. **Merkezi Kontrat Modülü (`smartpay-proto`)**: Tüm servislerin veri taşıyıcıları ve servis arayüzleri tek bir `.proto` reposunda toplanır; derleme anında Java Stub sınıfları otomatik üretilir.
2. **HTTP/2 Multiplexing**: Tek bir TCP bağlantısı üzerinden aynı anda yüzlerce eşzamanlı istek taşınır.
3. **İkili (Binary) Protobuf**: JSON'a kıyasla 5-10 kat daha hızlı serileştirme ve daha küçük paket boyutu sağlanır.

## Alternatifler
* **REST (JSON/HTTP 1.1)**: Dış dünyaya açık API Gateway uç noktalarında istemci uyumluluğu için tutulmuş, iç ağda elenmiştir.
* **Apache Thrift**: Topluluk ve bulut ekosistemi desteği gRPC kadar geniş değildir.

## Sonuçlar
* **Olumlu**: Çok daha düşük ağ gecikmesi (<2ms iç ağ), derleme anında zorunlu API uyumluluğu, çift yönlü streaming yeteneği.
* **Olumsuz**: Tarayıcılar doğrudan gRPC çağıramaz (API Gateway üzerinden REST -> gRPC köprüsü gerektirir); geliştiricilerin gRPC ve Protobuf araçlarına aşina olması gerekir.
