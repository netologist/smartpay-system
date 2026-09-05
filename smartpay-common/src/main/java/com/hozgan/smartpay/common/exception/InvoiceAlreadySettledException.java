package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.InvoiceId;

public final class InvoiceAlreadySettledException extends LogisticsException {

    private final InvoiceId invoiceId;

    public InvoiceAlreadySettledException(InvoiceId invoiceId) {
        super("ERR_INVOICE_ALREADY_SETTLED",
                String.format("Invoice %s has already been settled and cannot be modified or re-factored", invoiceId));
        this.invoiceId = invoiceId;
    }

    public InvoiceId invoiceId() {
        return invoiceId;
    }
}
