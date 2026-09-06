package com.hozgan.smartpay.invoice.dto.response;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;

import java.time.Instant;
import java.util.UUID;

public record EpodRecordResponse(
        UUID epodId,
        LoadId loadId,
        CarrierId carrierId,
        boolean verified,
        Instant deliveredAt,
        Instant createdAt
) {
    public static EpodRecordResponse from(EpodRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        return new EpodRecordResponse(
                entity.getId(),
                entity.getLoadId(),
                entity.getCarrierId(),
                entity.isVerified(),
                entity.getDeliveredAt(),
                entity.getCreatedAt()
        );
    }
}
