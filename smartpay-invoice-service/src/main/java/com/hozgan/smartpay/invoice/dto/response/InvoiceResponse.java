package com.hozgan.smartpay.invoice.dto.response;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceResponse(
        UUID invoiceId,
        String loadId,
        UUID shipperId,
        UUID carrierId,
        VehicleType vehicleType,
        BigDecimal mileageMiles,
        String currency,
        PricingBreakdownResponse pricing,
        InvoiceStatus status,
        Instant createdAt
) {
    public static InvoiceResponse from(InvoiceEntity entity) {
        return new InvoiceResponse(
                entity.getId(),
                entity.getLoadId(),
                entity.getShipperId(),
                entity.getCarrierId(),
                entity.getVehicleType(),
                entity.getMileageMiles(),
                entity.getCurrency(),
                PricingBreakdownResponse.from(entity.getPricing()),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
