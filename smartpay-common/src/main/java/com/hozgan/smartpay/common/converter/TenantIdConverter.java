package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.TenantId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TenantIdConverter implements AttributeConverter<TenantId, String> {

    @Override
    public String convertToDatabaseColumn(TenantId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TenantId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TenantId.of(dbData);
    }
}
