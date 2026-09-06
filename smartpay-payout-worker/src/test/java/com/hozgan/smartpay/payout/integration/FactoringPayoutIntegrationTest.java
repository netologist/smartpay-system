package com.hozgan.smartpay.payout.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.event.FactoringPayoutApprovedEvent;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome.FactoringStatus;
import com.hozgan.smartpay.payout.service.FactoringPayoutWorker;
import com.hozgan.smartpay.proto.payment.InitiatePaymentRequest;
import com.hozgan.smartpay.proto.payment.InitiatePaymentResponse;
import com.hozgan.smartpay.proto.payment.PaymentMethodProto;
import com.hozgan.smartpay.proto.payment.PaymentServiceGrpc;
import com.hozgan.smartpay.proto.payment.PaymentStatusProto;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskRequest;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskResponse;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import com.hozgan.smartpay.proto.risk.RiskTierProto;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.kafka.listener.auto-startup=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import(FactoringPayoutIntegrationTest.TestGrpcAndRestConfig.class)
@DisplayName("FactoringPayout — Full End-to-End Integration Tests")
class FactoringPayoutIntegrationTest {

    private static WireMockServer wireMockServer;
    private static Server grpcServer;
    private static final String SERVER_NAME = "InProcessFactoringServer";

    private static final AtomicReference<EvaluateCarrierRiskRequest> lastRiskRequest = new AtomicReference<>();
    private static final AtomicReference<InitiatePaymentRequest> lastPaymentRequest = new AtomicReference<>();
    private static volatile int mockRiskScore = 15;
    private static volatile boolean mockRiskApproved = true;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private FactoringPayoutWorker payoutWorker;

    @TestConfiguration
    static class TestGrpcAndRestConfig {

        @Bean
        @Primary
        public RestClient invoiceRestClient() {
            return RestClient.builder()
                    .baseUrl("http://localhost:" + wireMockServer.port())
                    .build();
        }

        @Bean
        @Primary
        public RiskServiceGrpc.RiskServiceBlockingStub riskServiceBlockingStub() {
            ManagedChannel channel = InProcessChannelBuilder.forName(SERVER_NAME)
                    .directExecutor()
                    .build();
            return RiskServiceGrpc.newBlockingStub(channel);
        }

        @Bean
        @Primary
        public PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub() {
            ManagedChannel channel = InProcessChannelBuilder.forName(SERVER_NAME)
                    .directExecutor()
                    .build();
            return PaymentServiceGrpc.newBlockingStub(channel);
        }
    }

    @BeforeAll
    static void startServers() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // In-process gRPC RiskService and PaymentService mocks
        RiskServiceGrpc.RiskServiceImplBase riskServiceImpl = new RiskServiceGrpc.RiskServiceImplBase() {
            @Override
            public void evaluateCarrierRisk(EvaluateCarrierRiskRequest request,
                                            StreamObserver<EvaluateCarrierRiskResponse> responseObserver) {
                lastRiskRequest.set(request);
                EvaluateCarrierRiskResponse response = EvaluateCarrierRiskResponse.newBuilder()
                        .setCarrierId(request.getCarrierId())
                        .setRiskScore(mockRiskScore)
                        .setRiskTier(mockRiskScore < 40 ? RiskTierProto.LOW : RiskTierProto.CRITICAL)
                        .setApproved(mockRiskApproved)
                        .setReasoning(mockRiskApproved ? "Creditworthiness approved" : "High fraud propensity")
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };

        PaymentServiceGrpc.PaymentServiceImplBase paymentServiceImpl = new PaymentServiceGrpc.PaymentServiceImplBase() {
            @Override
            public void initiatePayment(InitiatePaymentRequest request,
                                        StreamObserver<InitiatePaymentResponse> responseObserver) {
                lastPaymentRequest.set(request);
                InitiatePaymentResponse response = InitiatePaymentResponse.newBuilder()
                        .setPaymentId("PAY-INT-TEST-001")
                        .setStatus(PaymentStatusProto.INITIATED)
                        .setEndToEndId(request.getEndToEndId())
                        .setInitiatedAtEpochMs(System.currentTimeMillis())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };

        grpcServer = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(riskServiceImpl)
                .addService(paymentServiceImpl)
                .build()
                .start();
    }

    @AfterAll
    static void stopServers() {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        lastRiskRequest.set(null);
        lastPaymentRequest.set(null);
        mockRiskScore = 15;
        mockRiskApproved = true;

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("Full E2E Flow: AC-1 + AC-3 Factoring Advance Execution from ePOD to gRPC Payment & Kafka Event")
    void shouldExecuteEndToEndFactoringAdvanceSuccessfully() {
        // Given
        LoadId loadId = LoadId.of("LOAD-E2E-2026-001");
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        InvoiceId invoiceId = InvoiceId.generate();

        EpodVerifiedEvent epodEvent = EpodVerifiedEvent.of(
                loadId, carrierId, Instant.now(), GeoLocation.of(51.5074, -0.1278)
        );

        String invoiceJson = String.format("""
                {
                    "invoiceId": "%s",
                    "loadId": "%s",
                    "shipperId": "%s",
                    "carrierId": "%s",
                    "vehicleType": "ARTICULATED_LORRY",
                    "mileageMiles": 180.50,
                    "currency": "GBP",
                    "pricing": {
                        "baseAmount": { "amount": "920.00", "currency": "GBP" },
                        "fuelSurcharge": { "amount": "30.00", "currency": "GBP" },
                        "vatAmount": { "amount": "50.00", "currency": "GBP" },
                        "totalAmount": { "amount": "1000.00", "currency": "GBP" }
                    },
                    "status": "EPOD_VERIFIED",
                    "createdAt": "2026-09-05T12:00:00Z"
                }
                """, invoiceId.asString(), loadId.asString(), shipperId.asString(), carrierId.asString());

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/" + loadId.asString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(invoiceJson)));

        wireMockServer.stubFor(put(urlEqualTo("/api/v1/invoices/" + invoiceId.value() + "/status?status=FACTORING_APPROVED"))
                .willReturn(aResponse().withStatus(200)));

        // When: Execute factoring workflow
        FactoringPayoutOutcome outcome = payoutWorker.processDeliveryVerification(epodEvent);

        // Then: Outcome verified
        assertThat(outcome.status()).isEqualTo(FactoringStatus.APPROVED);
        assertThat(outcome.invoiceId()).isEqualTo(invoiceId);
        assertThat(outcome.carrierId()).isEqualTo(carrierId);
        assertThat(outcome.grossAmount()).isEqualTo(Money.of("1000.00", Money.GBP));
        assertThat(outcome.factoringFee()).isEqualTo(Money.of("25.00", Money.GBP));
        assertThat(outcome.netPayoutAmount()).isEqualTo(Money.of("975.00", Money.GBP));
        assertThat(outcome.paymentId()).isEqualTo("PAY-INT-TEST-001");

        // Assert gRPC RiskService was invoked
        EvaluateCarrierRiskRequest riskReq = lastRiskRequest.get();
        assertThat(riskReq).isNotNull();
        assertThat(riskReq.getCarrierId()).isEqualTo(carrierId.asString());
        assertThat(riskReq.getShipperId()).isEqualTo(shipperId.asString());
        assertThat(riskReq.getInvoiceAmount().getAmountInPence()).isEqualTo(100000L);

        // Assert gRPC PaymentService was invoked
        InitiatePaymentRequest payReq = lastPaymentRequest.get();
        assertThat(payReq).isNotNull();
        assertThat(payReq.getIdempotencyKey()).isEqualTo("FACTORING-ADVANCE-INV-" + invoiceId.asString());
        assertThat(payReq.getDebtorAccountId()).isEqualTo("0191c7a2-9b24-7f11-9a1c-3d842b10a512");
        assertThat(payReq.getCreditorAccountId()).isEqualTo(carrierId.asString());
        assertThat(payReq.getAmount().getAmountInPence()).isEqualTo(97500L); // £975.00
        assertThat(payReq.getPaymentMethod()).isEqualTo(PaymentMethodProto.FASTER_PAYMENTS);

        // Assert WireMock invoice status update
        wireMockServer.verify(WireMock.putRequestedFor(
                urlEqualTo("/api/v1/invoices/" + invoiceId.value() + "/status?status=FACTORING_APPROVED")));

        // Assert Kafka publication
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("smartpay.events.factoring"), eq(carrierId.asString()), eventCaptor.capture());

        Object published = eventCaptor.getValue();
        assertThat(published).isInstanceOf(FactoringPayoutApprovedEvent.class);
        FactoringPayoutApprovedEvent event = (FactoringPayoutApprovedEvent) published;
        assertThat(event.invoiceId()).isEqualTo(invoiceId);
        assertThat(event.carrierId()).isEqualTo(carrierId);
        assertThat(event.grossAmount()).isEqualTo(Money.of("1000.00", Money.GBP));
        assertThat(event.payoutAmount()).isEqualTo(Money.of("975.00", Money.GBP));
        assertThat(event.factoringFee()).isEqualTo(Money.of("25.00", Money.GBP));
    }

    @Test
    @DisplayName("Full E2E Flow: AC-2 Fraud Score Rejection halts advance before gRPC payment or invoice status update")
    void shouldHaltFactoringAdvanceWhenRiskScoreExceedsThresholdInE2E() {
        // Given: Risk score = 75 (> threshold 40)
        mockRiskScore = 75;
        mockRiskApproved = true;

        LoadId loadId = LoadId.of("LOAD-RISK-75");
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        InvoiceId invoiceId = InvoiceId.generate();

        EpodVerifiedEvent epodEvent = EpodVerifiedEvent.of(
                loadId, carrierId, Instant.now(), GeoLocation.of(51.5, -0.1)
        );

        String invoiceJson = String.format("""
                {
                    "invoiceId": "%s",
                    "loadId": "%s",
                    "shipperId": "%s",
                    "carrierId": "%s",
                    "vehicleType": "VAN",
                    "mileageMiles": 50.00,
                    "currency": "GBP",
                    "pricing": {
                        "baseAmount": { "amount": "450.00", "currency": "GBP" },
                        "fuelSurcharge": { "amount": "20.00", "currency": "GBP" },
                        "vatAmount": { "amount": "30.00", "currency": "GBP" },
                        "totalAmount": { "amount": "500.00", "currency": "GBP" }
                    },
                    "status": "EPOD_VERIFIED",
                    "createdAt": "2026-09-05T12:00:00Z"
                }
                """, invoiceId.asString(), loadId.asString(), shipperId.asString(), carrierId.asString());

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/" + loadId.asString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(invoiceJson)));

        // When
        FactoringPayoutOutcome outcome = payoutWorker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.RISK_REJECTED);
        assertThat(outcome.reasoning()).contains("Risk score=75");

        // Assert PaymentService was NEVER invoked
        assertThat(lastPaymentRequest.get()).isNull();

        // Assert invoice status was NEVER updated
        wireMockServer.verify(0, WireMock.putRequestedFor(WireMock.anyUrl()));
    }
}
