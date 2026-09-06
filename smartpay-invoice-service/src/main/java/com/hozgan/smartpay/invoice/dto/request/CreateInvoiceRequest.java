package com.hozgan.smartpay.invoice.dto.request;

import com.hozgan.smartpay.common.model.enums.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotBlank(message = "loadId cannot be blank")
        String loadId,

        @NotNull(message = "shipperId cannot be null")
        UUID shipperId,

        @NotNull(message = "carrierId cannot be null")
        UUID carrierId,

        @NotNull(message = "vehicleType cannot be null")
        VehicleType vehicleType,

        @NotNull(message = "mileageMiles cannot be null")
        @DecimalMin(value = "0.01", message = "mileageMiles must be strictly positive")
        BigDecimal mileageMiles,

        String currency
) {}
