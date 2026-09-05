package com.hozgan.smartpay.common.converter;

import com.hozgan.smartpay.common.model.id.AccountId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

@Converter(autoApply = true)
public class AccountIdConverter implements AttributeConverter<AccountId, UUID> {

    @Override
    public UUID convertToDatabaseColumn(AccountId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AccountId convertToEntityAttribute(UUID dbData) {
        return dbData == null ? null : AccountId.of(dbData);
    }
}
