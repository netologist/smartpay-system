package com.hozgan.smartpay.risk.service.fraud;

import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;
import com.hozgan.smartpay.risk.repository.FraudRuleEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-Factor Fraud Heuristics and Credit Risk Scoring Engine.
 * Evaluates automated pipeline rules against carrier profiles, velocity history,
 * and contextual factoring metadata.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudRuleEngine {

    public static final String REASON_COMPLIANT = "APPROVED_COMPLIANT_CARRIER";
    public static final String REASON_SANCTIONED = "ERR_CARRIER_SANCTIONED_OR_BLACKLISTED";
    public static final String REASON_SHIPPER_BLACKLISTED = "ERR_SHIPPER_BLACKLISTED";
    public static final String REASON_EXPOSURE_EXCEEDED = "EXPOSURE_CEILING_EXCEEDED";
    public static final String REASON_VELOCITY_ANOMALY = "ERR_VELOCITY_ANOMALY";
    public static final String REASON_RAPID_DELIVERY = "RAPID_INVOICE_TO_DELIVERY_DELTA";
    public static final String REASON_COLLUSION = "SHIPPER_CARRIER_COLLUSION";
    public static final String REASON_HISTORICAL_DEFAULTS = "HISTORICAL_DEFAULTS_RECORDED";
    public static final String REASON_HIGH_RISK_HOLD = "HIGH_RISK_SCORE_HOLD";

    private final FraudRuleEvaluationRepository fraudRuleEvaluationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Evaluates all fraud heuristics and computes the composite risk score.
     */
    public FraudEvaluationSummary evaluate(FraudEvaluationContext context) {
        CarrierRiskProfileEntity carrier = context.carrierProfile();
        List<RuleEvaluationResult> triggeredRules = new ArrayList<>();

        // 1. Sanctions & Blacklist Screening
        if (carrier.isSanctionedOrBlacklisted()) {
            RuleEvaluationResult sanction = RuleEvaluationResult.immediateReject(
                    "SANCTION_SCREENING", REASON_SANCTIONED);
            triggeredRules.add(sanction);
            return buildSummary(100, RiskTier.CRITICAL, false, REASON_SANCTIONED, triggeredRules);
        }

        if (context.shipperProfile().isPresent() && context.shipperProfile().get().isBlacklisted()) {
            RuleEvaluationResult shipperBlacklist = RuleEvaluationResult.immediateReject(
                    "SHIPPER_BLACKLIST_SCREENING", REASON_SHIPPER_BLACKLISTED);
            triggeredRules.add(shipperBlacklist);
            return buildSummary(100, RiskTier.CRITICAL, false, REASON_SHIPPER_BLACKLISTED, triggeredRules);
        }

        // 2. Exposure Ceiling Check
        long requestedPence = context.invoiceAmount().toMinorUnits();
        if (!carrier.canAccommodateExposure(requestedPence)) {
            RuleEvaluationResult exposure = RuleEvaluationResult.immediateReject(
                    "EXPOSURE_CEILING", REASON_EXPOSURE_EXCEEDED);
            triggeredRules.add(exposure);
            int score = Math.max(70, carrier.getBaseRiskScore());
            return buildSummary(score, RiskTier.CRITICAL, false, REASON_EXPOSURE_EXCEEDED, triggeredRules);
        }

        // 3. Rule 1: Velocity (> 3 factoring requests within 10 minutes)
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        int recentCount = fraudRuleEvaluationRepository.countByCarrierIdAndEvaluatedAtAfter(
                carrier.getCarrierId(), tenMinutesAgo);
        if (recentCount > 3) {
            triggeredRules.add(RuleEvaluationResult.triggered(
                    "VELOCITY_CHECK", 30, REASON_VELOCITY_ANOMALY));
        }

        // 4. Rule 2: Invoice-to-Delivery Delta (< 15 min between invoice creation and ePOD delivery)
        if (context.invoiceCreatedAt().isPresent() && context.epodVerifiedAt().isPresent()) {
            Duration delta = Duration.between(context.invoiceCreatedAt().get(), context.epodVerifiedAt().get());
            if (!delta.isNegative() && delta.toMinutes() < 15) {
                triggeredRules.add(RuleEvaluationResult.triggered(
                        "INVOICE_DELIVERY_DELTA", 25, REASON_RAPID_DELIVERY));
            }
        }

        // 5. Rule 3: Shipper-Carrier Collusion (Same bank account or IP subnet)
        if (checkCollusion(carrier, context.shipperProfile(), context.clientIp(), context.bankAccountNumber())) {
            triggeredRules.add(RuleEvaluationResult.triggered(
                    "SHIPPER_CARRIER_COLLUSION", 50, REASON_COLLUSION));
        }

        // 6. Historical Default History Check
        if (carrier.getHistoricalDefaultCount() > 0) {
            int penalty = Math.min(30, carrier.getHistoricalDefaultCount() * 10);
            triggeredRules.add(RuleEvaluationResult.triggered(
                    "HISTORICAL_DEFAULTS", penalty, REASON_HISTORICAL_DEFAULTS));
        }

        // Aggregate Composite Score [0..100]
        int score = carrier.getBaseRiskScore();
        for (RuleEvaluationResult rule : triggeredRules) {
            score += rule.scoreImpact();
        }
        int finalScore = Math.min(100, Math.max(0, score));
        RiskTier tier = RiskTier.fromScore(finalScore);
        boolean approved = tier.isApproved();

        String reasoning;
        if (approved) {
            reasoning = REASON_COMPLIANT;
        } else if (!triggeredRules.isEmpty()) {
            reasoning = triggeredRules.getFirst().reasoning();
        } else {
            reasoning = REASON_HIGH_RISK_HOLD;
        }

        return buildSummary(finalScore, tier, approved, reasoning, triggeredRules);
    }

    private boolean checkCollusion(CarrierRiskProfileEntity carrier,
                                   Optional<ShipperRiskProfileEntity> shipperOpt,
                                   Optional<String> clientIpOpt,
                                   Optional<String> bankAccountOpt) {
        if (shipperOpt.isEmpty()) {
            return false;
        }
        ShipperRiskProfileEntity shipper = shipperOpt.get();

        // Check matching bank accounts
        String carrierBank = carrier.getBankAccountNumber();
        String shipperBank = shipper.getBankAccountNumber();
        if (carrierBank != null && !carrierBank.isBlank() && carrierBank.equalsIgnoreCase(shipperBank)) {
            return true;
        }
        if (bankAccountOpt.isPresent() && !bankAccountOpt.get().isBlank()
                && bankAccountOpt.get().equalsIgnoreCase(shipperBank)) {
            return true;
        }

        // Check matching IP subnets (/24)
        String carrierIp = clientIpOpt.orElse(carrier.getLastKnownIp());
        String shipperIp = shipper.getLastKnownIp();
        return isSameSubnet(carrierIp, shipperIp);
    }

    private boolean isSameSubnet(String ip1, String ip2) {
        if (ip1 == null || ip2 == null || ip1.isBlank() || ip2.isBlank()) {
            return false;
        }
        if (ip1.equalsIgnoreCase(ip2)) {
            return true;
        }
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        if (parts1.length == 4 && parts2.length == 4) {
            return parts1[0].equals(parts2[0])
                    && parts1[1].equals(parts2[1])
                    && parts1[2].equals(parts2[2]);
        }
        return false;
    }

    private FraudEvaluationSummary buildSummary(int score,
                                                RiskTier tier,
                                                boolean approved,
                                                String primaryReasoning,
                                                List<RuleEvaluationResult> triggeredRules) {
        String json;
        try {
            json = objectMapper.writeValueAsString(triggeredRules);
        } catch (Exception e) {
            log.warn("Failed to serialize triggered rules: {}", e.getMessage());
            json = "[]";
        }
        return new FraudEvaluationSummary(score, tier, approved, primaryReasoning, triggeredRules, json);
    }
}
