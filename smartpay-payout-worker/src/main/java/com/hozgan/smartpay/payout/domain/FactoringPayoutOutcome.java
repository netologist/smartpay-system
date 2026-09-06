package com.hozgan.smartpay.payout.domain;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;

/**
 * Encapsulates the final status, audit trail, and reasoning for a factoring payout execution.
 */
public record FactoringPayoutOutcome(
        InvoiceId invoiceId,
        CarrierId carrierId,
        Money grossAmount,
        Money factoringFee,
        Money netPayoutAmount,
        String paymentId,
        FactoringStatus status,
        String reasoning
) {
    public enum FactoringStatus {
        APPROVED,
        RISK_REJECTED,
        INVOICE_NOT_ELIGIBLE,
        COLLUSION_DETECTED,
        LIMIT_EXCEEDED,
        PAYMENT_FAILED
    }

    public static FactoringPayoutOutcome approved(
            InvoiceId invoiceId, CarrierId carrierId,
            Money grossAmount, Money factoringFee, Money netPayoutAmount,
            String paymentId) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, grossAmount, factoringFee, netPayoutAmount,
                paymentId, FactoringStatus.APPROVED, "Factoring advance approved and payment initiated");
    }

    public static FactoringPayoutOutcome riskRejected(
            InvoiceId invoiceId, CarrierId carrierId, Money grossAmount, String reasoning) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, grossAmount, null, null,
                null, FactoringStatus.RISK_REJECTED, reasoning);
    }

    public static FactoringPayoutOutcome notEligible(
            InvoiceId invoiceId, CarrierId carrierId, String reasoning) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, null, null, null,
                null, FactoringStatus.INVOICE_NOT_ELIGIBLE, reasoning);
    }

    public static FactoringPayoutOutcome collusionDetected(
            InvoiceId invoiceId, CarrierId carrierId, String reasoning) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, null, null, null,
                null, FactoringStatus.COLLUSION_DETECTED, reasoning);
    }

    public static FactoringPayoutOutcome limitExceeded(
            InvoiceId invoiceId, CarrierId carrierId, Money grossAmount, String reasoning) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, grossAmount, null, null,
                null, FactoringStatus.LIMIT_EXCEEDED, reasoning);
    }

    public static FactoringPayoutOutcome paymentFailed(
            InvoiceId invoiceId, CarrierId carrierId, Money grossAmount, String reasoning) {
        return new FactoringPayoutOutcome(
                invoiceId, carrierId, grossAmount, null, null,
                null, FactoringStatus.PAYMENT_FAILED, reasoning);
    }
}
