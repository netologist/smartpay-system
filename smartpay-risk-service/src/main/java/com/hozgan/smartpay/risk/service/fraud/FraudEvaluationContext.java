package com.hozgan.smartpay.risk.service.fraud;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.risk.entity.CarrierRiskProfileEntity;
import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;

import java.time.Instant;
import java.util.Optional;

/**
 * Contextual parameters provided to the multi-factor fraud heuristics engine.
 */
public record FraudEvaluationContext(
        CarrierId carrierId,
        ShipperId shipperId,
        Money invoiceAmount,
        CarrierRiskProfileEntity carrierProfile,
        Optional<ShipperRiskProfileEntity> shipperProfile,
        Optional<Instant> invoiceCreatedAt,
        Optional<Instant> epodVerifiedAt,
        Optional<String> clientIp,
        Optional<String> bankAccountNumber
) {
}
