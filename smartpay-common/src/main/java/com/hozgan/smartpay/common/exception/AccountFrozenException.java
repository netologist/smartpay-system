package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.AccountId;

public final class AccountFrozenException extends AccountException {

    private final AccountId accountId;

    public AccountFrozenException(AccountId accountId, String reason) {
        super("ERR_ACCOUNT_FROZEN", String.format("Account %s is frozen: %s", accountId, reason));
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
