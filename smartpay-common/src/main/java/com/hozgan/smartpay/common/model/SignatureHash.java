package com.hozgan.smartpay.common.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record SignatureHash(String value) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    public SignatureHash {
        Objects.requireNonNull(value, "SignatureHash value cannot be null");
        if (!SHA256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("SignatureHash must be a valid 64-character hex string, got: " + value);
        }
    }

    public static SignatureHash of(String hash) {
        return new SignatureHash(hash);
    }

    @Override
    public String toString() {
        return value;
    }
}
