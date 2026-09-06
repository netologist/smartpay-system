package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.proto.common.MoneyProto;

import java.util.Currency;

/**
 * Package-private mapper between domain {@link Money} and the generated {@link MoneyProto}.
 * Mirrors the conversion logic in the ledger service without creating a cross-module dependency.
 */
final class PaymentGrpcMapper {

    private PaymentGrpcMapper() {}

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
