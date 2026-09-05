package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IdempotencyKeyConverter implements AttributeConverter<IdempotencyKey, String> {

    @Override
    public String convertToDatabaseColumn(IdempotencyKey attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public IdempotencyKey convertToEntityAttribute(String dbData) {
        return dbData == null ? null : IdempotencyKey.of(dbData);
    }
}
