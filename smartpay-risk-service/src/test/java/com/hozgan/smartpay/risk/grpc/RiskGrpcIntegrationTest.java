package com.hozgan.smartpay.risk.grpc;

import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskRequest;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskResponse;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import com.hozgan.smartpay.proto.risk.RiskTierProto;
import com.hozgan.smartpay.risk.TestcontainersConfiguration;
import com.hozgan.smartpay.risk.entity.CarrierProfileStatus;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.FraudRuleEvaluationEntity;
import com.hozgan.smartpay.risk.entity.ShipperProfileStatus;
import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;
import com.hozgan.smartpay.risk.repository.CarrierRiskProfileRepository;
import com.hozgan.smartpay.risk.repository.FraudRuleEvaluationRepository;
import com.hozgan.smartpay.risk.repository.ShipperRiskProfileRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("RiskGrpcService — Full E2E Integration Tests (Testcontainers PostgreSQL 16)")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class RiskGrpcIntegrationTest {

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private CarrierRiskProfileRepository carrierRiskProfileRepository;

    @Autowired
    private ShipperRiskProfileRepository shipperRiskProfileRepository;

    @Autowired
    private FraudRuleEvaluationRepository fraudRuleEvaluationRepository;

    private ManagedChannel channel;
    private RiskServiceGrpc.RiskServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() {
        int port = grpcServerLifecycle.getPort();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = RiskServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // ── AC-1: Approved Risk Evaluation for Compliant Carrier ─────────────────────────
    @Test
    @DisplayName("AC-1: Compliant carrier with clean history evaluates to approved=true and score=18 (Tier: LOW)")
    void ac1_compliantCarrier_returnsApprovedLowTierScore18() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();

        // Seed carrier risk profile with £20,000 credit limit and clean score of 18
        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                20_000_00L, // £20,000
                0L,
                "GBP",
                18,
                0,
                "GB99TEST123456",
                "10-20-30",
                "192.168.1.5"
        );
        carrierRiskProfileRepository.save(carrier);

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(1200_00L) // £1,200.00
                        .build())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        assertThat(response.getCarrierId()).isEqualTo(carrierId.toString());
        assertThat(response.getApproved()).isTrue();
        assertThat(response.getRiskScore()).isEqualTo(18);
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.LOW);
        assertThat(response.getReasoning()).isEqualTo("APPROVED_COMPLIANT_CARRIER");

        // Verify active exposure updated in PostgreSQL
        CarrierRiskProfileEntity updatedCarrier = carrierRiskProfileRepository.findById(carrierId).orElseThrow();
        assertThat(updatedCarrier.getCurrentActiveFactoringPence()).isEqualTo(1200_00L);

        // Verify audit trail in PostgreSQL
        List<FraudRuleEvaluationEntity> audits = fraudRuleEvaluationRepository.findByCarrierIdOrderByEvaluatedAtDesc(carrierId);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().isApproved()).isTrue();
        assertThat(audits.getFirst().getRiskScore()).isEqualTo(18);
    }

    // ── AC-2: Fraud Score Rejection Above Threshold (Velocity Anomaly) ───────────────
    @Test
    @DisplayName("AC-2: Carrier with velocity anomaly is rejected with score=72 and reasoning ERR_VELOCITY_ANOMALY")
    void ac2_velocityAnomaly_returnsRejectedCriticalTierScore72() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();

        // Seed carrier with base score 42
        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                42, // Base score 42
                0,
                null, null, null
        );
        carrierRiskProfileRepository.save(carrier);

        // Seed 4 prior evaluations in the last 5 minutes to trigger velocity anomaly (> 3 requests)
        Instant recent = Instant.now().minus(2, ChronoUnit.MINUTES);
        for (int i = 0; i < 4; i++) {
            fraudRuleEvaluationRepository.save(new FraudRuleEvaluationEntity(
                    UUID.randomUUID(),
                    carrierId,
                    shipperId,
                    500_00L,
                    "GBP",
                    20,
                    RiskTier.LOW,
                    true,
                    "PRIOR_TEST_EVALUATION",
                    "[]"
            ));
        }

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(1000_00L)
                        .build())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        assertThat(response.getApproved()).isFalse();
        assertThat(response.getRiskScore()).isEqualTo(72); // 42 base + 30 velocity = 72
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.CRITICAL);
        assertThat(response.getReasoning()).isEqualTo("ERR_VELOCITY_ANOMALY");

        // Verify active exposure was NOT incremented
        CarrierRiskProfileEntity updatedCarrier = carrierRiskProfileRepository.findById(carrierId).orElseThrow();
        assertThat(updatedCarrier.getCurrentActiveFactoringPence()).isEqualTo(0L);
    }

    // ── AC-3: Exposure Limit Enforcement ─────────────────────────────────────────────
    @Test
    @DisplayName("AC-3: Credit limit £10,000 with active £9,500 rejects new invoice of £1,000")
    void ac3_exposureLimitExceeded_returnsRejectedExposureCeilingExceeded() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();

        // Seed carrier with £10,000 limit and £9,500 active factoring
        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                10_000_00L, // Max credit limit £10,000
                9_500_00L,  // Current active factoring £9,500
                "GBP",
                18,
                0,
                null, null, null
        );
        carrierRiskProfileRepository.save(carrier);

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(1000_00L) // £1,000 (total = £10,500 > £10,000)
                        .build())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        assertThat(response.getApproved()).isFalse();
        assertThat(response.getReasoning()).isEqualTo("EXPOSURE_CEILING_EXCEEDED");
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.CRITICAL);

        // Verify active exposure remained £9,500
        CarrierRiskProfileEntity updatedCarrier = carrierRiskProfileRepository.findById(carrierId).orElseThrow();
        assertThat(updatedCarrier.getCurrentActiveFactoringPence()).isEqualTo(9_500_00L);
    }

    // ── Sanction & Blacklist Screening ───────────────────────────────────────────────
    @Test
    @DisplayName("Sanctioned carrier is immediately rejected with score=100")
    void sanctionedCarrier_returnsImmediateReject100() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();

        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.SANCTIONED,
                50_000_00L,
                0L,
                "GBP",
                18,
                0,
                null, null, null
        );
        carrierRiskProfileRepository.save(carrier);

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(500_00L)
                        .build())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        assertThat(response.getApproved()).isFalse();
        assertThat(response.getRiskScore()).isEqualTo(100);
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.CRITICAL);
        assertThat(response.getReasoning()).isEqualTo("ERR_CARRIER_SANCTIONED_OR_BLACKLISTED");
    }

    // ── Shipper-Carrier Collusion Rule ────────────────────────────────────────────────
    @Test
    @DisplayName("Matching bank account between carrier and shipper triggers collusion rule (+50 pts)")
    void shipperCarrierCollusion_sameBankAccount_triggersCollusionAnomaly() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        String sharedBankAccount = "GB12BARC20000012345678";

        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                15,
                0,
                sharedBankAccount,
                "20-00-00",
                "10.10.1.1"
        );
        carrierRiskProfileRepository.save(carrier);

        ShipperRiskProfileEntity shipper = new ShipperRiskProfileEntity(
                shipperId,
                ShipperProfileStatus.ACTIVE,
                sharedBankAccount, // Same bank account!
                "20-00-00",
                "10.20.2.2"
        );
        shipperRiskProfileRepository.save(shipper);

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(1000_00L)
                        .build())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        // Base 15 + Collusion 50 = 65 (Tier: HIGH, approved=false)
        assertThat(response.getApproved()).isFalse();
        assertThat(response.getRiskScore()).isEqualTo(65);
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.HIGH);
        assertThat(response.getReasoning()).isEqualTo("SHIPPER_CARRIER_COLLUSION");
    }

    // ── Rapid Invoice-to-Delivery Delta Rule ──────────────────────────────────────────
    @Test
    @DisplayName("Rapid delivery delta (< 15 min) increases risk score by 25 pts")
    void rapidDeliveryDelta_addsRiskPoints() {
        UUID carrierId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();

        CarrierRiskProfileEntity carrier = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                10, // Base 10
                0,
                null, null, null
        );
        carrierRiskProfileRepository.save(carrier);

        Instant now = Instant.now();
        Instant invoiceCreated = now.minus(10, ChronoUnit.MINUTES);
        Instant epodVerified = now.minus(2, ChronoUnit.MINUTES); // 8 minutes delta < 15 min

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.toString())
                .setShipperId(shipperId.toString())
                .setInvoiceAmount(MoneyProto.newBuilder()
                        .setCurrency("GBP")
                        .setAmountInPence(1000_00L)
                        .build())
                .setInvoiceCreatedAtEpochMs(invoiceCreated.toEpochMilli())
                .setEpodVerifiedAtEpochMs(epodVerified.toEpochMilli())
                .build();

        EvaluateCarrierRiskResponse response = blockingStub.evaluateCarrierRisk(request);

        // Base 10 + Rapid Delta 25 = 35 (Tier: MEDIUM, approved with reduced advance rate)
        assertThat(response.getApproved()).isTrue();
        assertThat(response.getRiskScore()).isEqualTo(35);
        assertThat(response.getRiskTier()).isEqualTo(RiskTierProto.MEDIUM);
    }

    // ── Validation Errors ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("Missing carrier_id throws gRPC INVALID_ARGUMENT")
    void missingCarrierId_throwsInvalidArgument() {
        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId("")
                .setShipperId(UUID.randomUUID().toString())
                .setInvoiceAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(1000).build())
                .build();

        assertThatThrownBy(() -> blockingStub.evaluateCarrierRisk(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }
}
