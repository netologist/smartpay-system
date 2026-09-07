package com.hozgan.smartpay.risk.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.risk.entity.CarrierProfileStatus;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.FraudRuleEvaluationEntity;
import com.hozgan.smartpay.risk.repository.CarrierRiskProfileRepository;
import com.hozgan.smartpay.risk.repository.FraudRuleEvaluationRepository;
import com.hozgan.smartpay.risk.repository.ShipperRiskProfileRepository;
import com.hozgan.smartpay.risk.service.fraud.FraudEvaluationContext;
import com.hozgan.smartpay.risk.service.fraud.FraudEvaluationSummary;
import com.hozgan.smartpay.risk.service.fraud.FraudRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskEvaluationService Unit Tests")
class RiskEvaluationServiceTest {

    @Mock
    private CarrierRiskProfileRepository carrierRiskProfileRepository;

    @Mock
    private ShipperRiskProfileRepository shipperRiskProfileRepository;

    @Mock
    private FraudRuleEvaluationRepository fraudRuleEvaluationRepository;

    @Mock
    private FraudRuleEngine fraudRuleEngine;

    @InjectMocks
    private RiskEvaluationService riskEvaluationService;

    @Test
    @DisplayName("Approved risk evaluation increments active factoring exposure and persists audit record")
    void approvedEvaluation_incrementsExposureAndSavesAudit() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1200.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                20_000_00L,
                1_000_00L,
                "GBP",
                18,
                0,
                null, null, null
        );

        when(carrierRiskProfileRepository.findById(carrierId.value()))
                .thenReturn(Optional.of(carrierProfile));
        when(shipperRiskProfileRepository.findById(shipperId.value()))
                .thenReturn(Optional.empty());

        FraudEvaluationSummary summary = new FraudEvaluationSummary(
                18, RiskTier.LOW, true, FraudRuleEngine.REASON_COMPLIANT, List.of(), "[]"
        );
        when(fraudRuleEngine.evaluate(any(FraudEvaluationContext.class))).thenReturn(summary);

        EvaluateCarrierRiskCommand command = EvaluateCarrierRiskCommand.of(carrierId, shipperId, invoiceAmount);

        RiskEvaluationResult result = riskEvaluationService.evaluateCarrierRisk(command);

        assertThat(result.approved()).isTrue();
        assertThat(result.riskScore()).isEqualTo(18);
        assertThat(result.riskTier()).isEqualTo(RiskTier.LOW);

        // Verify carrier exposure incremented: 1,000 + 1,200 = 2,200 (£2,200)
        assertThat(carrierProfile.getCurrentActiveFactoringPence()).isEqualTo(2_200_00L);
        verify(carrierRiskProfileRepository).save(carrierProfile);

        // Verify audit log persisted
        ArgumentCaptor<FraudRuleEvaluationEntity> auditCaptor = ArgumentCaptor.forClass(FraudRuleEvaluationEntity.class);
        verify(fraudRuleEvaluationRepository).save(auditCaptor.capture());
        FraudRuleEvaluationEntity audit = auditCaptor.getValue();
        assertThat(audit.getCarrierId()).isEqualTo(carrierId.value());
        assertThat(audit.getInvoiceAmountPence()).isEqualTo(1200_00L);
        assertThat(audit.isApproved()).isTrue();
        assertThat(audit.getRiskScore()).isEqualTo(18);
    }

    @Test
    @DisplayName("Rejected risk evaluation does NOT increment active exposure but persists audit record")
    void rejectedEvaluation_doesNotIncrementExposureAndSavesAudit() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("1000.00", Money.GBP);

        CarrierRiskProfileEntity carrierProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                10_000_00L,
                9_500_00L,
                "GBP",
                18,
                0,
                null, null, null
        );

        when(carrierRiskProfileRepository.findById(carrierId.value()))
                .thenReturn(Optional.of(carrierProfile));
        when(shipperRiskProfileRepository.findById(shipperId.value()))
                .thenReturn(Optional.empty());

        FraudEvaluationSummary summary = new FraudEvaluationSummary(
                70, RiskTier.CRITICAL, false, FraudRuleEngine.REASON_EXPOSURE_EXCEEDED, List.of(), "[]"
        );
        when(fraudRuleEngine.evaluate(any(FraudEvaluationContext.class))).thenReturn(summary);

        EvaluateCarrierRiskCommand command = EvaluateCarrierRiskCommand.of(carrierId, shipperId, invoiceAmount);

        RiskEvaluationResult result = riskEvaluationService.evaluateCarrierRisk(command);

        assertThat(result.approved()).isFalse();
        assertThat(result.reasoning()).isEqualTo(FraudRuleEngine.REASON_EXPOSURE_EXCEEDED);

        // Verify carrier exposure NOT incremented
        assertThat(carrierProfile.getCurrentActiveFactoringPence()).isEqualTo(9_500_00L);
        verify(carrierRiskProfileRepository, never()).save(carrierProfile);

        // Verify audit log still persisted
        verify(fraudRuleEvaluationRepository).save(any(FraudRuleEvaluationEntity.class));
    }

    @Test
    @DisplayName("Unknown carrier is auto-provisioned with compliant default profile")
    void unknownCarrier_autoProvisionsDefaultProfile() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money invoiceAmount = Money.of("500.00", Money.GBP);

        when(carrierRiskProfileRepository.findById(carrierId.value()))
                .thenReturn(Optional.empty());

        CarrierRiskProfileEntity defaultProfile = new CarrierRiskProfileEntity(
                carrierId.value(),
                CarrierProfileStatus.ACTIVE,
                RiskEvaluationService.DEFAULT_MAX_CREDIT_LIMIT_PENCE,
                0L,
                "GBP",
                RiskEvaluationService.DEFAULT_BASE_RISK_SCORE,
                0,
                null, null, null
        );
        when(carrierRiskProfileRepository.save(any(CarrierRiskProfileEntity.class)))
                .thenReturn(defaultProfile);

        FraudEvaluationSummary summary = new FraudEvaluationSummary(
                18, RiskTier.LOW, true, FraudRuleEngine.REASON_COMPLIANT, List.of(), "[]"
        );
        when(fraudRuleEngine.evaluate(any(FraudEvaluationContext.class))).thenReturn(summary);

        EvaluateCarrierRiskCommand command = EvaluateCarrierRiskCommand.of(carrierId, shipperId, invoiceAmount);

        RiskEvaluationResult result = riskEvaluationService.evaluateCarrierRisk(command);

        assertThat(result.approved()).isTrue();
        verify(carrierRiskProfileRepository, org.mockito.Mockito.times(2)).save(any(CarrierRiskProfileEntity.class));
    }
}
