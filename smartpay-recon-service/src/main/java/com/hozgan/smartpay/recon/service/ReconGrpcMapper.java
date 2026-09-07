package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.proto.common.MoneyProto;

import java.util.Currency;

/**
 * Package-private mapper between domain {@link Money} and the generated {@link MoneyProto}.
 * Isolated from the payment service's copy; this one lives in the recon bounded context.
 */
final class ReconGrpcMapper {

    private ReconGrpcMapper() {}

    static MoneyProto toMoneyProto(Money money) {
        return MoneyProto.newBuilder()
                .setCurrency(money.currency().getCurrencyCode())
                .setAmountInPence(money.toMinorUnits())
                .build();
    }

    static Money toMoney(MoneyProto proto) {
        Currency currency = Currency.getInstance(proto.getCurrency());
        return Money.ofMinor(proto.getAmountInPence(), currency);
    }
}
