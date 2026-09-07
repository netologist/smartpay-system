package com.hozgan.smartpay.risk.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.util.UuidV7;
import com.hozgan.smartpay.risk.entity.CarrierProfileStatus;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.FraudRuleEvaluationEntity;
import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;
import com.hozgan.smartpay.risk.repository.CarrierRiskProfileRepository;
import com.hozgan.smartpay.risk.repository.FraudRuleEvaluationRepository;
import com.hozgan.smartpay.risk.repository.ShipperRiskProfileRepository;
import com.hozgan.smartpay.risk.service.fraud.FraudEvaluationContext;
import com.hozgan.smartpay.risk.service.fraud.FraudEvaluationSummary;
import com.hozgan.smartpay.risk.service.fraud.FraudRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEvaluationService {

    public static final long DEFAULT_MAX_CREDIT_LIMIT_PENCE = 25_000_00L; // £25,000 default credit limit
    public static final int DEFAULT_BASE_RISK_SCORE = 18;

    private final CarrierRiskProfileRepository carrierRiskProfileRepository;
    private final ShipperRiskProfileRepository shipperRiskProfileRepository;
    private final FraudRuleEvaluationRepository fraudRuleEvaluationRepository;
    private final FraudRuleEngine fraudRuleEngine;

    /**
     * Evaluates credit risk, active factoring ceiling, and multi-factor fraud heuristics for a carrier.
     */
    @Transactional
    public RiskEvaluationResult evaluateCarrierRisk(EvaluateCarrierRiskCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(command.carrierId(), "carrierId cannot be null");
        Objects.requireNonNull(command.shipperId(), "shipperId cannot be null");
        Objects.requireNonNull(command.invoiceAmount(), "invoiceAmount cannot be null");

        UUID carrierUuid = command.carrierId().value();
        UUID shipperUuid = command.shipperId().value();

        log.debug("Evaluating carrier risk: carrierId={}, shipperId={}, invoiceAmount={}",
                carrierUuid, shipperUuid, command.invoiceAmount());

        CarrierRiskProfileEntity carrierProfile = carrierRiskProfileRepository.findById(carrierUuid)
                .orElseGet(() -> createDefaultCarrierProfile(carrierUuid));

        Optional<ShipperRiskProfileEntity> shipperProfile = shipperRiskProfileRepository.findById(shipperUuid);

        FraudEvaluationContext context = new FraudEvaluationContext(
                command.carrierId(),
                command.shipperId(),
                command.invoiceAmount(),
                carrierProfile,
                shipperProfile,
                command.invoiceCreatedAt(),
                command.epodVerifiedAt(),
                command.clientIp(),
                command.bankAccountNumber()
        );

        FraudEvaluationSummary summary = fraudRuleEngine.evaluate(context);

        if (summary.approved()) {
            carrierProfile.addActiveExposure(command.invoiceAmount().toMinorUnits());
            carrierRiskProfileRepository.save(carrierProfile);
            log.info("Factoring advance approved for carrier {}: score={}, exposure=+{}",
                    carrierUuid, summary.finalScore(), command.invoiceAmount());
        } else {
            log.warn("Factoring advance rejected for carrier {}: score={}, reason={}",
                    carrierUuid, summary.finalScore(), summary.primaryReasoning());
        }

        // Persist audit record
        FraudRuleEvaluationEntity auditRecord = new FraudRuleEvaluationEntity(
                UuidV7.generate(),
                carrierUuid,
                shipperUuid,
                command.invoiceAmount().toMinorUnits(),
                command.invoiceAmount().currency().getCurrencyCode(),
                summary.finalScore(),
                summary.riskTier(),
                summary.approved(),
                summary.primaryReasoning(),
                summary.rulesTriggeredJson()
        );
        fraudRuleEvaluationRepository.save(auditRecord);

        return new RiskEvaluationResult(
                command.carrierId(),
                summary.finalScore(),
                summary.riskTier(),
                summary.approved(),
                summary.primaryReasoning()
        );
    }

    private CarrierRiskProfileEntity createDefaultCarrierProfile(UUID carrierId) {
        log.info("Creating default active carrier risk profile for: {}", carrierId);
        CarrierRiskProfileEntity profile = new CarrierRiskProfileEntity(
                carrierId,
                CarrierProfileStatus.ACTIVE,
                DEFAULT_MAX_CREDIT_LIMIT_PENCE,
                0L,
                "GBP",
                DEFAULT_BASE_RISK_SCORE,
                0,
                null,
                null,
                null
        );
        return carrierRiskProfileRepository.save(profile);
    }
}
