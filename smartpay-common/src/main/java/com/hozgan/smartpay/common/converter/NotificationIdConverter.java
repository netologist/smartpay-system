package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.NotificationId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class NotificationIdConverter implements AttributeConverter<NotificationId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(NotificationId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NotificationId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : NotificationId.of(dbData);
    }
}
