package com.hozgan.smartpay.invoice.dto.response;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
        InvoiceId invoiceId,
        LoadId loadId,
        ShipperId shipperId,
        CarrierId carrierId,
        VehicleType vehicleType,
        BigDecimal mileageMiles,
        String currency,
        PricingBreakdownResponse pricing,
        InvoiceStatus status,
        Instant createdAt
) {
    public static InvoiceResponse from(InvoiceEntity entity) {
        if (entity == null) {
            return null;
        }
        return new InvoiceResponse(
                entity.getInvoiceId(),
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
