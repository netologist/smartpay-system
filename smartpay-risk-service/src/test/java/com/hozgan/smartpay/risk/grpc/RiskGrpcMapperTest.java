package com.hozgan.smartpay.risk.grpc;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.RiskTier;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.risk.RiskTierProto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("RiskGrpcMapper Unit Tests")
class RiskGrpcMapperTest {

    @Test
    @DisplayName("Maps RiskTier domain enum to RiskTierProto bidirectional")
    void shouldMapRiskTierBidirectionally() {
        assertThat(RiskGrpcMapper.toProto(RiskTier.LOW)).isEqualTo(RiskTierProto.LOW);
        assertThat(RiskGrpcMapper.toProto(RiskTier.MEDIUM)).isEqualTo(RiskTierProto.MEDIUM);
        assertThat(RiskGrpcMapper.toProto(RiskTier.HIGH)).isEqualTo(RiskTierProto.HIGH);
        assertThat(RiskGrpcMapper.toProto((RiskTier) null)).isEqualTo(RiskTierProto.RISK_TIER_UNSPECIFIED);
        assertThat(RiskGrpcMapper.toDomain(RiskTierProto.LOW)).isEqualTo(RiskTier.LOW);
        assertThat(RiskGrpcMapper.toDomain(RiskTierProto.MEDIUM)).isEqualTo(RiskTier.MEDIUM);
        assertThat(RiskGrpcMapper.toDomain(RiskTierProto.HIGH)).isEqualTo(RiskTier.HIGH);
        assertThat(RiskGrpcMapper.toDomain(RiskTierProto.CRITICAL)).isEqualTo(RiskTier.CRITICAL);
        assertThat(RiskGrpcMapper.toDomain(RiskTierProto.RISK_TIER_UNSPECIFIED)).isEqualTo(RiskTier.CRITICAL);
    }

    @Test
    @DisplayName("Maps Money to MoneyProto bidirectional")
    void shouldMapMoneyBidirectionally() {
        Money money = Money.of("1234.56", Money.GBP);
        MoneyProto proto = RiskGrpcMapper.toProto(money);

        assertThat(proto.getCurrency()).isEqualTo("GBP");
        assertThat(proto.getAmountInPence()).isEqualTo(123456L);

        Money reconstructed = RiskGrpcMapper.toMoney(proto);
        assertThat(reconstructed).isEqualTo(money);
    }
}
