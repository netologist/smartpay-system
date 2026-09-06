package com.hozgan.smartpay.invoice.entity;

import com.hozgan.smartpay.common.exception.InvoiceAlreadySettledException;
import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class InvoiceEntityTest {

    private InvoicePricing createSamplePricing() {
        Money base = Money.ofGBP("525.00");
        Money fuel = Money.ofGBP("63.00");
        Money vat = Money.ofGBP("117.60");
        return InvoicePricing.calculate(base, fuel, vat);
    }

    @Test
    @DisplayName("AC-5: Settlement Mutability Lock prevents cancellation of SETTLED invoice")
    void ac5_settledInvoiceCannotBeCancelled() {
        InvoiceEntity invoice = new InvoiceEntity(
                LoadId.of("LOAD-UK-0841"),
                ShipperId.generate(),
                CarrierId.generate(),
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                createSamplePricing(),
                InvoiceStatus.SETTLED
        );

        assertThatThrownBy(invoice::cancel)
                .isInstanceOf(InvoiceAlreadySettledException.class)
                .hasMessageContaining("has already been settled and cannot be modified");
    }

    @Test
    @DisplayName("AC-5: Settlement Mutability Lock prevents status update of SETTLED invoice")
    void ac5_settledInvoiceCannotBeUpdated() {
        InvoiceEntity invoice = new InvoiceEntity(
                LoadId.of("LOAD-UK-0841"),
                ShipperId.generate(),
                CarrierId.generate(),
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                createSamplePricing(),
                InvoiceStatus.SETTLED
        );

        assertThatThrownBy(() -> invoice.updateStatus(InvoiceStatus.FACTORING_APPROVED))
                .isInstanceOf(InvoiceAlreadySettledException.class)
                .hasMessageContaining("has already been settled and cannot be modified");
    }

    @Test
    @DisplayName("Non-settled invoice can be cancelled or transitioned")
    void nonSettledInvoiceCanBeModified() {
        InvoiceEntity invoice = new InvoiceEntity(
                LoadId.of("LOAD-UK-0841"),
                ShipperId.generate(),
                CarrierId.generate(),
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                createSamplePricing(),
                InvoiceStatus.EPOD_VERIFIED
        );

        invoice.updateStatus(InvoiceStatus.FACTORING_APPROVED);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FACTORING_APPROVED);

        invoice.cancel();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    @DisplayName("Entity correctly exposes reconstructed InvoicePricing value object")
    void pricingReconstruction() {
        InvoicePricing original = createSamplePricing();
        InvoiceEntity invoice = new InvoiceEntity(
                LoadId.of("LOAD-UK-0841"),
                ShipperId.generate(),
                CarrierId.generate(),
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                original,
                InvoiceStatus.EPOD_VERIFIED
        );

        InvoicePricing reconstructed = invoice.getPricing();
        assertThat(reconstructed.baseAmount()).isEqualTo(original.baseAmount());
        assertThat(reconstructed.fuelSurcharge()).isEqualTo(original.fuelSurcharge());
        assertThat(reconstructed.vatAmount()).isEqualTo(original.vatAmount());
        assertThat(reconstructed.totalAmount()).isEqualTo(original.totalAmount());
    }
}
