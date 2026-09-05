package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.CarrierId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class CarrierIdConverter implements AttributeConverter<CarrierId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(CarrierId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CarrierId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : CarrierId.of(dbData);
    }
}
