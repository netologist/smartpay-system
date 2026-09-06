package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.AccountId;

/**
 * Thrown when the Ledger service rejects a hold/payment request because the debtor account
 * has insufficient available balance to cover the requested amount.
 */
public final class InsufficientFundsForPaymentException extends PaymentException {

    private final AccountId accountId;

    public InsufficientFundsForPaymentException(AccountId accountId) {
        super("ERR_INSUFFICIENT_FUNDS_FOR_PAYMENT",
                String.format("Account %s has insufficient funds to cover the requested payment", accountId));
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
