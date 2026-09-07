package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.PaymentId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class PaymentIdConverter implements AttributeConverter<PaymentId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(PaymentId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public PaymentId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : PaymentId.of(dbData);
    }
}
