package com.hozgan.smartpay.gateway.web;

/**
 * Canonical RFC 7807 error type URIs emitted by the edge gateway (see STORY-006 contracts).
 */
public final class ProblemTypes {

    private ProblemTypes() {
    }

    public static final String MISSING_IDEMPOTENCY_KEY = "https://smartpay.internal/errors/missing-idempotency-key";
    public static final String IDEMPOTENCY_CONFLICT = "https://smartpay.internal/errors/idempotency-conflict";
    public static final String REQUEST_HASH_MISMATCH = "https://smartpay.internal/errors/request-hash-mismatch";
    public static final String UNAUTHORIZED = "https://smartpay.internal/errors/unauthorized";
    public static final String RATE_LIMIT_EXCEEDED = "https://smartpay.internal/errors/rate-limit-exceeded";
    public static final String ROUTE_NOT_FOUND = "https://smartpay.internal/errors/route-not-found";
    public static final String BAD_GATEWAY = "https://smartpay.internal/errors/bad-gateway";
    public static final String FORBIDDEN_ORIGIN = "https://smartpay.internal/errors/cors-origin-forbidden";
}
