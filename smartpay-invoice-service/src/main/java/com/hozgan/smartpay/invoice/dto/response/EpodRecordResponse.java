package com.hozgan.smartpay.invoice.dto.response;

import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;

import java.time.Instant;
import java.util.UUID;

public record EpodRecordResponse(
        UUID epodId,
        String loadId,
        UUID carrierId,
        boolean verified,
        Instant deliveredAt,
        Instant createdAt
) {
    public static EpodRecordResponse from(EpodRecordEntity entity) {
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
