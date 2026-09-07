package com.hozgan.smartpay.risk.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;

import java.time.Instant;
import java.util.Optional;

public record EvaluateCarrierRiskCommand(
        CarrierId carrierId,
        ShipperId shipperId,
        Money invoiceAmount,
        Optional<Instant> invoiceCreatedAt,
        Optional<Instant> epodVerifiedAt,
        Optional<String> clientIp,
        Optional<String> bankAccountNumber
) {
    public static EvaluateCarrierRiskCommand of(CarrierId carrierId, ShipperId shipperId, Money invoiceAmount) {
        return new EvaluateCarrierRiskCommand(
                carrierId, shipperId, invoiceAmount,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
