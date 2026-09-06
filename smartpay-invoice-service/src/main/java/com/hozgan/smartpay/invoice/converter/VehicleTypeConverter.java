package com.hozgan.smartpay.invoice.converter;

import com.hozgan.smartpay.common.model.enums.VehicleType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VehicleTypeConverter implements AttributeConverter<VehicleType, String> {

    @Override
    public String convertToDatabaseColumn(VehicleType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.dbCode();
    }

    @Override
    public VehicleType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return VehicleType.fromDbCode(dbData);
    }
}
