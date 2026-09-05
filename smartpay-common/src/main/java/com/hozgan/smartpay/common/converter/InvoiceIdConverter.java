package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.InvoiceId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class InvoiceIdConverter implements AttributeConverter<InvoiceId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(InvoiceId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public InvoiceId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : InvoiceId.of(dbData);
    }
}
