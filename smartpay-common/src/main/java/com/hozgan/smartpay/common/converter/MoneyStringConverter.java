package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Currency;

@Converter(autoApply = false)
public class MoneyStringConverter implements AttributeConverter<Money, String> {

    @Override
    public String convertToDatabaseColumn(Money attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public Money convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String[] parts = dbData.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Money string format, expected '<CURRENCY> <AMOUNT>', got: " + dbData);
        }
        return Money.of(parts[1], Currency.getInstance(parts[0]));
    }
}
