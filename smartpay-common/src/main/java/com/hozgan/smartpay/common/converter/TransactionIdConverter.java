package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.TransactionId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class TransactionIdConverter implements AttributeConverter<TransactionId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(TransactionId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TransactionId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : TransactionId.of(dbData);
    }
}
