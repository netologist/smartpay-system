package com.hozgan.smartpay.invoice.dto.request;

import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateInvoiceRequest(
        @NotNull(message = "loadId cannot be null")
        LoadId loadId,

        @NotNull(message = "shipperId cannot be null")
        ShipperId shipperId,

        @NotNull(message = "carrierId cannot be null")
        CarrierId carrierId,

        @NotNull(message = "vehicleType cannot be null")
        VehicleType vehicleType,

        @NotNull(message = "mileageMiles cannot be null")
        @DecimalMin(value = "0.01", message = "mileageMiles must be strictly positive")
        BigDecimal mileageMiles,

        String currency
) {}
