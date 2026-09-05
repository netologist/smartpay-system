# gRPC & Protocol Buffers Technical Guide (Spring Boot 4.1 & Modern Java 25)

## 1. What is gRPC and Why We Choose It Over REST for Internal Calls
gRPC (Google Remote Procedure Call) is a modern, open-source RPC framework that enables microservices to communicate with the semantics of a local method invocation over **HTTP/2**.

### REST (JSON over HTTP/1.1) vs gRPC (Protobuf over HTTP/2)
| Dimension | Traditional REST (JSON) | gRPC (Protocol Buffers) |
| :--- | :--- | :--- |
| **Serialization** | Text-based JSON (high CPU, verbose parsing) | Binary Protocol Buffers (dense, ultra-fast serialization) |
| **Network Protocol** | HTTP/1.1 (new TCP handshake per connection) | HTTP/2 (multiplexing over a single persistent TCP connection) |
| **Type Safety** | Optional / advisory (OpenAPI / Swagger) | Strict compile-time contract enforcement (`.proto`) |
| **Performance** | Moderate (latency spikes under financial SLAs) | 7x to 10x lower CPU and network latency |
| **Code Generation** | Manual DTOs and client implementations | Automated Java message and stub generation via `protoc` |

---

## 2. Platform Architecture in `smartpay-proto`

When `smartpay-proto` is compiled (`mvn compile -pl smartpay-proto`), it scans `.proto` schema files and produces generated Java classes under `target/generated-sources/protobuf/`:

### A) Message Classes (Data Carriers)
The `protoc` compiler generates an immutable Java class and a corresponding `Builder` for each protobuf `message`:
```java
// Producing a Protobuf message (Builder pattern):
MoneyProto moneyProto = MoneyProto.newBuilder()
        .setCurrency("GBP")
        .setAmountInPence(1050) // 10.50 GBP
        .build();

// Consuming from a Protobuf message:
String currency = moneyProto.getCurrency();
long pence = moneyProto.getAmountInPence();
```

### B) Service Grpc Classes (Client & Server Stubs)
For `service LedgerService` defined in `ledger.proto`:
* `LedgerServiceGrpc.LedgerServiceImplBase`: The abstract server class to extend when implementing the service.
* `LedgerServiceGrpc.LedgerServiceBlockingStub`: The synchronous client stub used for blocking RPC calls.
* `LedgerServiceGrpc.LedgerServiceStub`: The asynchronous, reactive client stub.

---

## 3. Implementing a gRPC Server in Spring Boot

### Understanding `StreamObserver<T>`
gRPC uses the reactive `StreamObserver<T>` interface rather than direct method returns:
1. `responseObserver.onNext(response)`: Emits the response message to the client.
2. `responseObserver.onCompleted()`: Closes the stream, signaling successful RPC completion.
3. `responseObserver.onError(throwable)`: Terminates the RPC with an error status.

### Example Server Implementation: `LedgerGrpcService.java`
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
            // 1. Map from Protobuf DTO to smartpay-common domain types
            AccountId accountId = AccountId.of(request.getAccountId());

            // 2. Execute domain service logic
            AccountBalance balance = balanceService.getBalance(accountId);

            // 3. Map domain result back to Protobuf response
            GetBalanceResponse response = GetBalanceResponse.newBuilder()
                    .setAccountId(balance.accountId().asString())
                    .setClearedBalance(toMoneyProto(balance.clearedBalance()))
                    .setHoldBalance(toMoneyProto(balance.holdBalance()))
                    .setAvailableBalance(toMoneyProto(balance.availableBalance()))
                    .setVersion(balance.version())
                    .build();

            // 4. Send response and complete call
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InsufficientFundsException ex) {
            // Translate domain exception to standard gRPC Status code (FAILED_PRECONDITION)
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription(ex.getMessage())
                    .asRuntimeException());
        } catch (Exception ex) {
            // Translate unexpected errors to gRPC Status INTERNAL
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal ledger error: " + ex.getMessage())
                    .asRuntimeException());
        }
    }

    private MoneyProto toMoneyProto(Money money) {
        return MoneyProto.newBuilder()
                .setCurrency(money.currency().getCurrencyCode())
                .setAmountInPence(money.toMinorUnits())
                .build();
    }
}
```

---

## 4. Invoking Another Microservice via gRPC Client Stub

Example: `smartpay-payment-service` calling `smartpay-ledger-service` to hold funds.

### A) Channel and Stub Configuration
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
            @Value("${smartpay.ledger.grpc.port:9091}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // In production environments, configure TLS/mTLS
                .build();
    }

    @Bean
    public LedgerServiceGrpc.LedgerServiceBlockingStub ledgerBlockingStub(ManagedChannel ledgerChannel) {
        return LedgerServiceGrpc.newBlockingStub(ledgerChannel);
    }
}
```

### B) Executing the RPC in Application Service
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

            // Synchronous RPC call over HTTP/2 binary stream
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
                // Handle precondition error from peer service
            }
            throw new SmartpayDomainException("GRPC_COMMUNICATION_ERROR", ex.getMessage(), ex);
        }
    }
}
```

---

## 5. Golden Rules for `smartpay-common` & gRPC Integration

1. **Protobuf Messages Must Never Leak Into Persistence**:
   * Protobuf classes (`MoneyProto`, `GetBalanceResponse`) are strictly network transport DTOs.
   * Entities and repositories must always use `smartpay-common` domain models (`Money`, `AccountId`, `AccountBalanceEntity`).
2. **Boundary Transformation (Anti-Corruption Layer)**:
   * Transform `Request Proto -> Domain Model` immediately at the gRPC controller boundary.
   * Transform `Domain Model -> Response Proto` before calling `responseObserver.onNext()`.
3. **Status Code Mapping**:
   * Map `SmartpayDomainException` subclasses to standard gRPC `Status` codes (`INVALID_ARGUMENT`, `FAILED_PRECONDITION`, `NOT_FOUND`, `ALREADY_EXISTS`) so client stubs receive structured error signals.
