package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;

public final class InsufficientFundsException extends AccountException {

    private final AccountId accountId;
    private final Money requestedAmount;
    private final Money availableBalance;

    public InsufficientFundsException(AccountId accountId, Money requestedAmount, Money availableBalance) {
        super("ERR_INSUFFICIENT_FUNDS",
                String.format("Account %s has insufficient funds. Requested: %s, Available: %s",
                        accountId, requestedAmount, availableBalance));
        this.accountId = accountId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money requestedAmount() {
        return requestedAmount;
    }

    public Money availableBalance() {
        return availableBalance;
    }
}
