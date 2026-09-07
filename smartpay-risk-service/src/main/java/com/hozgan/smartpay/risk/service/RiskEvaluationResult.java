package com.hozgan.smartpay.risk.service;

import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.common.model.id.CarrierId;

public record RiskEvaluationResult(
        CarrierId carrierId,
        int riskScore,
        RiskTier riskTier,
        boolean approved,
        String reasoning
) {
}
