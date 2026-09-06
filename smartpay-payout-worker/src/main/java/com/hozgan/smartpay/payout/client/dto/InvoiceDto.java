package com.hozgan.smartpay.payout.client.dto;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceDto(
        InvoiceId invoiceId,
        LoadId loadId,
        ShipperId shipperId,
        CarrierId carrierId,
        String vehicleType,
        BigDecimal mileageMiles,
        String currency,
        PricingDto pricing,
        InvoiceStatus status,
        Instant createdAt
) {
    public record PricingDto(
            Money baseAmount,
            Money fuelSurcharge,
            Money vatAmount,
            Money totalAmount
    ) {}
}
