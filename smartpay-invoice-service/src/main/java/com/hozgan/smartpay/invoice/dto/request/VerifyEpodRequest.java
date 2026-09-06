package com.hozgan.smartpay.invoice.dto.request;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record VerifyEpodRequest(
        @NotNull(message = "loadId cannot be null")
        LoadId loadId,

        @NotNull(message = "carrierId cannot be null")
        CarrierId carrierId,

        @NotNull(message = "deliveredAt cannot be null")
        Instant deliveredAt,

        @NotNull(message = "latitude cannot be null")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        BigDecimal latitude,

        @NotNull(message = "longitude cannot be null")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        BigDecimal longitude,

        @NotBlank(message = "photoS3Url cannot be blank")
        String photoS3Url,

        @NotBlank(message = "signatureHash cannot be blank")
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "signatureHash must be a 64-character hex string")
        String signatureHash
) {}
