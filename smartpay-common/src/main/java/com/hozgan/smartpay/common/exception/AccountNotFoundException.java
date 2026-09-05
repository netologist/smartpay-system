package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.AccountId;

public final class AccountNotFoundException extends AccountException {

    private final AccountId accountId;

    public AccountNotFoundException(AccountId accountId) {
        super("ERR_ACCOUNT_NOT_FOUND", "Account not found with ID: " + accountId);
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
