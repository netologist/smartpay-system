# gRPC & Protocol Buffers Teknik Rehberi (Spring Boot 4.1 & Modern Java 25)

## 1. gRPC Nedir ve Neden REST Yerine Tercih Ettik?
gRPC (Google Remote Procedure Call), mikroservislerin birbirleriyle doğrudan fonksiyon çağrısı yapar gibi haberleşmesini sağlayan, **HTTP/2** protokolü üzerinde çalışan yüksek performanslı bir RPC çerçevesidir.

### REST (JSON over HTTP/1.1) vs gRPC (Protobuf over HTTP/2)
| Özellik | Klasik REST (JSON) | gRPC (Protocol Buffers) |
| :--- | :--- | :--- |
| **Serileştirme** | Metin tabanlı JSON (Ağır, yavaş ayrıştırma) | İkili (Binary) Protobuf (Kompakt, aşırı hızlı) |
| **Ağ Protokolü** | HTTP/1.1 (Her istek için yeni TCP/el sıkışma) | HTTP/2 (Tek TCP bağlantısı üzerinden Multiplexing) |
| **Tip Güvenliği** | İsteğe bağlı (OpenAPI/Swagger) | Zorunlu derleme zamanı tip güvenliği (`.proto`) |
| **Performans** | Orta (Finansal SLA'ler için gecikme yaratabilir) | 7 ila 10 kat daha düşük CPU ve ağ gecikmesi |
| **Kod Üretimi** | Manuel DTO/Client yazımı | `protoc` ile otomatik Java Stub/Message üretimi |

---

## 2. Projemizdeki gRPC Mimarisi (`smartpay-proto`)

`smartpay-proto` modülümüz derlendiğinde (`mvn compile -pl smartpay-proto`), `.proto` dosyalarını okuyarak `target/generated-sources/protobuf/` altına iki tür Java sınıfı üretir:

### A) Message Sınıfları (Veri Taşıyıcılar)
`protoc` derleyicisi her `message` için immutable (değiştirilemez) bir Java sınıfı ve bir `Builder` üretir.
```java
// Protobuf nesnesi ÜRETME (Builder Deseni):
MoneyProto moneyProto = MoneyProto.newBuilder()
        .setCurrency("GBP")
        .setAmountInPence(1050) // 10.50 GBP
        .build();

// Protobuf nesnesinden OKUMA:
String currency = moneyProto.getCurrency();
long pence = moneyProto.getAmountInPence();
```

### B) Service Grpc Sınıfları (İstemci ve Sunucu İskeletleri)
Örneğin `ledger.proto` içindeki `service LedgerService`:
* `LedgerServiceGrpc.LedgerServiceImplBase`: **Sunucu (Server)** tarafında extend edeceğimiz soyut sınıf.
* `LedgerServiceGrpc.LedgerServiceBlockingStub`: **İstemci (Client)** tarafında senkron çağrı yapacağımız stub.
* `LedgerServiceGrpc.LedgerServiceStub`: Reaktif / Asenkron istemci stub'ı.

---

## 3. gRPC Sunucusu (Server) Nasıl Yazılır?

### `StreamObserver<T>` Mantığı
gRPC, klasik `return Response` yerine reaktif `StreamObserver<T>` yapısını kullanır.
Bir metoda istek geldiğinde yanıt şu 3 adımdan biriyle verilir:
1. `responseObserver.onNext(response)`: İstemciye yanıt nesnesini gönderir.
2. `responseObserver.onCompleted()`: Yanıt akışının bittiğini ve isteğin başarıyla kapandığını bildirir.
3. `responseObserver.onError(throwable)`: Bir hata oluştuğunu ve isteğin iptal olduğunu bildirir.

### Örnek: `LedgerGrpcService.java`
```java
package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.ledger.GetBalanceRequest;
import com.hozgan.smartpay.proto.ledger.GetBalanceResponse;
import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class LedgerGrpcService extends LedgerServiceGrpc.LedgerServiceImplBase {

    private final AccountBalanceService balanceService;

    public LedgerGrpcService(AccountBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> responseObserver) {
        try {
            // 1. Protobuf tipinden smartpay-common tipine çevir
            AccountId accountId = AccountId.of(request.getAccountId());

            // 2. Domain servisini çağır
            AccountBalance balance = balanceService.getBalance(accountId);

            // 3. Domain modelini Protobuf yanıtına dönüştür
            GetBalanceResponse response = GetBalanceResponse.newBuilder()
                    .setAccountId(balance.accountId().asString())
                    .setClearedBalance(toMoneyProto(balance.clearedBalance()))
                    .setHoldBalance(toMoneyProto(balance.holdBalance()))
                    .setAvailableBalance(toMoneyProto(balance.availableBalance()))
                    .setVersion(balance.version())
                    .build();

            // 4. Yanıtı ilet ve isteği tamamla
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InsufficientFundsException ex) {
            // Domain hatasını gRPC Status hatasına çevir (HTTP 400/422 muadili FAILED_PRECONDITION)
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription(ex.getMessage())
                    .asRuntimeException());
        } catch (Exception ex) {
            // Beklenmeyen sistem hataları (HTTP 500 muadili INTERNAL)
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal ledger error: " + ex.getMessage())
                    .asRuntimeException());
        }
    }

    // Mapper Yardımcısı (smartpay-common -> smartpay-proto)
    private MoneyProto toMoneyProto(Money money) {
        return MoneyProto.newBuilder()
                .setCurrency(money.currency().getCurrencyCode())
                .setAmountInPence(money.toMinorUnits())
                .build();
    }
}
```

---

## 4. Bir Mikroservis Başka Bir Mikroservisi gRPC ile Nasıl Çağırır (Client Stub)?

Örneğin `smartpay-payment-service`, bakiye kontrolü için `smartpay-ledger-service`'i çağırırken:

### A) Channel ve Stub Yapılandırması (Configuration Bean)
```java
package com.hozgan.smartpay.payment.config;

import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean
    public ManagedChannel ledgerChannel(
            @Value("${smartpay.ledger.grpc.host:localhost}") String host,
            @Value("${smartpay.ledger.grpc.port:9090}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // Prod ortamında mTLS/TLS kullanılır
                .build();
    }

    @Bean
    public LedgerServiceGrpc.LedgerServiceBlockingStub ledgerBlockingStub(ManagedChannel ledgerChannel) {
        return LedgerServiceGrpc.newBlockingStub(ledgerChannel);
    }
}
```

### B) Servis İçinde Kullanım (Client Çağrısı)
```java
@Service
public class PaymentProcessingService {

    private final LedgerServiceGrpc.LedgerServiceBlockingStub ledgerStub;

    public PaymentProcessingService(LedgerServiceGrpc.LedgerServiceBlockingStub ledgerStub) {
        this.ledgerStub = ledgerStub;
    }

    public void verifyBalance(AccountId accountId, Money amount) {
        try {
            GetBalanceRequest request = GetBalanceRequest.newBuilder()
                    .setAccountId(accountId.asString())
                    .build();

            // Senkron gRPC çağrısı (Ağ üzerinden binary veri akışı)
            GetBalanceResponse response = ledgerStub.getBalance(request);

            Money available = Money.ofMinor(
                    response.getAvailableBalance().getAmountInPence(),
                    Currency.getInstance(response.getAvailableBalance().getCurrency())
            );

            if (!available.atLeast(amount)) {
                throw new InsufficientFundsException(accountId, amount, available);
            }

        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                // Karşı servisten gelen domain hatasını yönet
            }
            throw new SmartpayDomainException("GRPC_COMMUNICATION_ERROR", ex.getMessage(), ex);
        }
    }
}
```

---

## 5. `smartpay-common` ile gRPC Arasındaki Altın Kurallar

1. **Protobuf Modelleri Veritabanına Yazılmaz**:
   * Protobuf sınıfları (`MoneyProto`, `GetBalanceResponse`) yalnızca ağ taşıma katmanıdır (DTO).
   * Veritabanı ve iş mantığında **daima `smartpay-common` sınıfları** (`Money`, `AccountId`, `AccountBalanceEntity`) kullanılır.
2. **Sınırda Dönüşüm (Boundary Mapping)**:
   * gRPC servisine istek girer girmez `request -> common domain records` çevrimi yapılır.
   * Domain servisi işini bitirince `common domain records -> response proto` çevrilip geri verilir.
3. **Exception Çevrimi**:
   * Domain'de fırlatılan `SmartpayDomainException` alt sınıfları gRPC katmanında uygun `Status` koduna (`INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `NOT_FOUND`, `ALREADY_EXISTS`) dönüştürülmelidir.
