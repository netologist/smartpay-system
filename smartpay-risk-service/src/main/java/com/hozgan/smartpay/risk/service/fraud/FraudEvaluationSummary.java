package com.hozgan.smartpay.risk.service.fraud;

import com.hozgan.smartpay.common.model.enums.RiskTier;

import java.util.List;

public record FraudEvaluationSummary(
        int finalScore,
        RiskTier riskTier,
        boolean approved,
        String primaryReasoning,
        List<RuleEvaluationResult> triggeredRules,
        String rulesTriggeredJson
) {
}
