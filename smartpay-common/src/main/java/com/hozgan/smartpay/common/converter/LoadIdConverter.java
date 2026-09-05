package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.LoadId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LoadIdConverter implements AttributeConverter<LoadId, String> {

    @Override
    public String convertToDatabaseColumn(LoadId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public LoadId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LoadId.of(dbData);
    }
}
