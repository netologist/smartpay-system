package com.hozgan.smartpay.risk.service.fraud;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.risk.entity.CarrierProfileStatus;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.ShipperProfileStatus;
import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;
import com.hozgan.smartpay.risk.repository.FraudRuleEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("FraudRuleEngine Unit Tests")
class FraudRuleEngineTest {

    @Mock
    private FraudRuleEvaluationRepository fraudRuleEvaluationRepository;

    @InjectMocks
    private FraudRuleEngine fraudRuleEngine;

    // ── AC-1: Approved Risk Evaluation for Compliant Carrier ─────────────────────────
    @Test
    @DisplayName("AC-1: Compliant carrier with clean history returns approved=true and score=18 (Tier: LOW)")
    void ac1_compliantCarrier_returnsApprovedLowTierScore18() {
        // Given: Active carrier with clean history and base score of 18 (Tier: LOW)
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1200.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                20_000_00L, // £20,000 credit limit
                0L,         // £0 current active factoring
                "GBP",
                18,         // Base risk score = 18
                0,          // Historical defaults = 0
                "GB29NWBK60161331926819",
                "60-16-13",
                "192.168.1.50"
        );

        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(0);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId,
                shipperId,
                invoiceAmount,
                carrierProfile,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When
        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Then
        assertThat(summary.approved()).isTrue();
        assertThat(summary.finalScore()).isEqualTo(18);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.LOW);
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_COMPLIANT);
        assertThat(summary.triggeredRules()).isEmpty();
    }

    // ── AC-2: Fraud Score Rejection Above Threshold (Velocity Anomaly) ───────────────
    @Test
    @DisplayName("AC-2: Carrier flagged for velocity anomalies with composite risk score of 72 (Tier: CRITICAL)")
    void ac2_velocityAnomaly_returnsRejectedCriticalTierScore72() {
        // Given: Carrier with base score 42, flagged for > 3 requests in 10 minutes (+30 points -> 72)
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1500.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                5_000_00L,
                "GBP",
                42,         // Base score = 42
                0,
                "GB29NWBK60161331926819",
                "60-16-13",
                "10.0.0.1"
        );

        // Velocity condition: 4 recent factoring requests within 10 minutes
        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(4);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId,
                shipperId,
                invoiceAmount,
                carrierProfile,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When
        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Then: Score 42 + 30 = 72 (Tier: CRITICAL), approved = false, reasoning ERR_VELOCITY_ANOMALY
        assertThat(summary.approved()).isFalse();
        assertThat(summary.finalScore()).isEqualTo(72);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.CRITICAL);
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_VELOCITY_ANOMALY);
        assertThat(summary.triggeredRules()).hasSize(1);
        assertThat(summary.triggeredRules().getFirst().ruleName()).isEqualTo("VELOCITY_CHECK");
        assertThat(summary.triggeredRules().getFirst().scoreImpact()).isEqualTo(30);
    }

    // ── AC-3: Exposure Limit Enforcement ─────────────────────────────────────────────
    @Test
    @DisplayName("AC-3: Credit limit £10,000 with active factoring £9,500 rejects new invoice of £1,000")
    void ac3_exposureLimitExceeded_returnsRejectedExposureCeilingExceeded() {
        // Given: Credit limit £10,000, current active £9,500, new request £1,000 -> cumulative £10,500
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                10_000_00L, // Max credit limit: £10,000
                9_500_00L,  // Active factoring: £9,500
                "GBP",
                18,
                0,
                "GB29NWBK60161331926819",
                "60-16-13",
                "10.0.0.1"
        );

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId,
                shipperId,
                invoiceAmount,
                carrierProfile,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // When
        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Then: approved = false, reasoning = EXPOSURE_CEILING_EXCEEDED
        assertThat(summary.approved()).isFalse();
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_EXPOSURE_EXCEEDED);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.CRITICAL);
        assertThat(summary.finalScore()).isGreaterThanOrEqualTo(70);
    }

    // ── Sanction & Blacklist Screening ───────────────────────────────────────────────
    @Test
    @DisplayName("Sanctioned or Blacklisted carrier receives immediate rejection with score 100")
    void sanctionedCarrier_returnsImmediateReject100() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("500.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.SANCTIONED,
                50_000_00L,
                0L,
                "GBP",
                18,
                0,
                null, null, null
        );

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        assertThat(summary.approved()).isFalse();
        assertThat(summary.finalScore()).isEqualTo(100);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.CRITICAL);
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_SANCTIONED);
    }

    @Test
    @DisplayName("Blacklisted shipper receives immediate rejection with score 100")
    void blacklistedShipper_returnsImmediateReject100() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("500.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                18,
                0,
                null, null, null
        );

        ShipperRiskProfileEntity shipperProfile = new ShipperRiskProfileEntity(
                shipperId.value(),
                ShipperProfileStatus.BLACKLISTED,
                "GB82WEST12345678",
                "12-34-56",
                "10.0.0.2"
        );

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.of(shipperProfile), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        assertThat(summary.approved()).isFalse();
        assertThat(summary.finalScore()).isEqualTo(100);
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_SHIPPER_BLACKLISTED);
    }

    // ── Rule 2: Invoice-to-Delivery Delta ────────────────────────────────────────────
    @Test
    @DisplayName("ePOD verified < 15 min after invoice creation triggers rapid delivery delta rule (+25 pts)")
    void rapidInvoiceToDeliveryDelta_adds25Points() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                10, // Base 10
                0,
                null, null, null
        );

        Instant invoiceCreatedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant epodVerifiedAt = Instant.now().minus(2, ChronoUnit.MINUTES); // 8 minutes delta < 15 min

        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(0);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.empty(),
                Optional.of(invoiceCreatedAt),
                Optional.of(epodVerifiedAt),
                Optional.empty(),
                Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Base 10 + 25 = 35 (Tier: MEDIUM, approved with reduced advance rate)
        assertThat(summary.finalScore()).isEqualTo(35);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.MEDIUM);
        assertThat(summary.approved()).isTrue();
        assertThat(summary.triggeredRules()).anyMatch(r -> r.ruleName().equals("INVOICE_DELIVERY_DELTA"));
    }

    // ── Rule 3: Shipper-Carrier Collusion ────────────────────────────────────────────
    @Test
    @DisplayName("Matching bank accounts between carrier and shipper triggers collusion rule (+50 pts)")
    void shipperCarrierCollusion_sameBankAccount_adds50Points() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                15,
                0,
                "GB29NWBK60161331926819",
                "60-16-13",
                "192.168.1.10"
        );

        ShipperRiskProfileEntity shipperProfile = new ShipperRiskProfileEntity(
                shipperId.value(),
                ShipperProfileStatus.ACTIVE,
                "GB29NWBK60161331926819", // SAME bank account as carrier!
                "60-16-13",
                "10.0.0.5"
        );

        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(0);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.of(shipperProfile),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Base 15 + 50 = 65 (Tier: HIGH, automatic hold)
        assertThat(summary.finalScore()).isEqualTo(65);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.HIGH);
        assertThat(summary.approved()).isFalse();
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_COLLUSION);
    }

    @Test
    @DisplayName("Matching IP /24 subnet between carrier and shipper triggers collusion rule (+50 pts)")
    void shipperCarrierCollusion_sameIpSubnet_adds50Points() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                15,
                0,
                "GB1111", "11-11-11",
                "192.168.4.15" // /24 subnet: 192.168.4
        );

        ShipperRiskProfileEntity shipperProfile = new ShipperRiskProfileEntity(
                shipperId.value(),
                ShipperProfileStatus.ACTIVE,
                "GB2222", "22-22-22",
                "192.168.4.99" // Same /24 subnet: 192.168.4!
        );

        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(0);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.of(shipperProfile),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Base 15 + 50 = 65
        assertThat(summary.finalScore()).isEqualTo(65);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.HIGH);
        assertThat(summary.approved()).isFalse();
        assertThat(summary.primaryReasoning()).isEqualTo(FraudRuleEngine.REASON_COLLUSION);
    }

    // ── Historical Defaults Penalty ──────────────────────────────────────────────────
    @Test
    @DisplayName("Carrier with historical defaults receives score penalty")
    void historicalDefaults_addsPenaltiesUpToCap() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                50_000_00L,
                0L,
                "GBP",
                10,
                2, // 2 historical defaults = +20 pts
                null, null, null
        );

        when(fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(eq(carrierId.value()), any(Instant.class)))
                .thenReturn(0);

        FraudEvaluationContext context = new FraudEvaluationContext(
                carrierId, shipperId, invoiceAmount, carrierProfile,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        // Base 10 + 20 = 30 (Tier: MEDIUM)
        assertThat(summary.finalScore()).isEqualTo(30);
        assertThat(summary.riskTier()).isEqualTo(RiskTier.MEDIUM);
        assertThat(summary.approved()).isTrue();
    }
}
