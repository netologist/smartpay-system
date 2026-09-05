package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.ledger.EntryTypeProto;

import java.util.Currency;
import java.util.UUID;

/**
 * Utility mapper between Protocol Buffer representations and SmartPay domain models.
 */
public final class LedgerGrpcMapper {

    private LedgerGrpcMapper() {
        // utility class
    }

    public static Money toMoney(MoneyProto proto) {
        if (proto == null || proto.getCurrency().isBlank()) {
            throw new IllegalArgumentException("MoneyProto must contain a valid ISO 4217 currency");
        }
        Currency currency = Currency.getInstance(proto.getCurrency());
        return Money.ofMinor(proto.getAmountInPence(), currency);
    }

    public static MoneyProto toMoneyProto(Money money) {
        if (money == null) {
            return MoneyProto.getDefaultInstance();
        }
        return MoneyProto.newBuilder()
                .setCurrency(money.currency().getCurrencyCode())
                .setAmountInPence(money.toMinorUnits())
                .build();
    }

    public static UUID toUUID(String uuidStr, String fieldName) {
        if (uuidStr == null || uuidStr.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        try {
            return UUID.fromString(uuidStr.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid UUID format for " + fieldName + ": " + uuidStr, ex);
        }
    }

    public static EntryType toEntryType(EntryTypeProto proto) {
        return switch (proto) {
            case DEBIT -> EntryType.DEBIT;
            case CREDIT -> EntryType.CREDIT;
            default -> throw new IllegalArgumentException("Unsupported or unspecified EntryTypeProto: " + proto);
        };
    }
}
