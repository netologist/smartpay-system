package com.hozgan.smartpay.common.exception;

import java.util.Currency;

public final class CurrencyMismatchException extends LedgerException {

    private final Currency expectedCurrency;
    private final Currency actualCurrency;

    public CurrencyMismatchException(Currency expectedCurrency, Currency actualCurrency) {
        super("ERR_CURRENCY_MISMATCH",
                String.format("Currency mismatch: expected %s, got %s", expectedCurrency, actualCurrency));
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
    }

    public Currency expectedCurrency() {
        return expectedCurrency;
    }

    public Currency actualCurrency() {
        return actualCurrency;
    }
}
