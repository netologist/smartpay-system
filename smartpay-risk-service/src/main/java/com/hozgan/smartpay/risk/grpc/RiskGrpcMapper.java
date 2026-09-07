package com.hozgan.smartpay.risk.grpc;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.risk.RiskTierProto;

import java.util.Currency;
import java.util.Objects;

public final class RiskGrpcMapper {

    private RiskGrpcMapper() {
    }

    public static RiskTierProto toProto(RiskTier tier) {
        if (tier == null) {
            return RiskTierProto.RISK_TIER_UNSPECIFIED;
        }
        return switch (tier) {
            case LOW -> RiskTierProto.LOW;
            case MEDIUM -> RiskTierProto.MEDIUM;
            case HIGH -> RiskTierProto.HIGH;
            case CRITICAL -> RiskTierProto.CRITICAL;
        };
    }

    public static RiskTier toDomain(RiskTierProto proto) {
        if (proto == null) {
            return RiskTier.CRITICAL;
        }
        return switch (proto) {
            case LOW -> RiskTier.LOW;
            case MEDIUM -> RiskTier.MEDIUM;
            case HIGH -> RiskTier.HIGH;
            case CRITICAL, RISK_TIER_UNSPECIFIED, UNRECOGNIZED -> RiskTier.CRITICAL;
        };
    }

    public static Money toMoney(MoneyProto proto) {
        Objects.requireNonNull(proto, "MoneyProto cannot be null");
        String currencyCode = proto.getCurrency().isBlank() ? "GBP" : proto.getCurrency();
        return Money.ofMinor(proto.getAmountInPence(), Currency.getInstance(currencyCode));
    }

    public static MoneyProto toProto(Money money) {
        Objects.requireNonNull(money, "Money cannot be null");
        return MoneyProto.newBuilder()
                .setCurrency(money.currency().getCurrencyCode())
                .setAmountInPence(money.toMinorUnits())
                .build();
    }
}
