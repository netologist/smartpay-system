package com.hozgan.smartpay.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PaymentRequest(
        @NotBlank @Size(max = 64)
        @JsonProperty("tenantId")
        String tenantId,

        @NotNull
        @JsonProperty("debtorAccountId")
        UUID debtorAccountId,

        @NotNull
        @JsonProperty("creditorAccountId")
        UUID creditorAccountId,

        @Min(1)
        @JsonProperty("amountInPence")
        long amountInPence,

        @NotBlank @Size(min = 3, max = 3)
        @JsonProperty("currency")
        String currency,

        @NotBlank
        @JsonProperty("paymentMethod")
        String paymentMethod,

        @NotBlank @Size(max = 255)
        @JsonProperty("reference")
        String reference,

        @NotBlank
        @JsonProperty("creditorSortCode")
        String creditorSortCode,

        @NotBlank
        @JsonProperty("creditorAccountNumber")
        String creditorAccountNumber
) {
    public String currencyCode() {
        return currency;
    }
}
