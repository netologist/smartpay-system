package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.ShipperId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class ShipperIdConverter implements AttributeConverter<ShipperId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(ShipperId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public ShipperId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : ShipperId.of(dbData);
    }
}
