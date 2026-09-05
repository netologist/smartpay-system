package com.hozgan.smartpay.common.model.enums;

public enum EntryType {
    DEBIT,
    CREDIT;

    public EntryType opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
