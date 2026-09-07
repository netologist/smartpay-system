package com.hozgan.smartpay.gateway.web.filter;

import org.springframework.core.Ordered;

/**
 * Execution order of the edge filter chain (lowest value runs first):
 * security headers/CORS → rate limiting → JWT authentication → idempotency + reverse proxy.
 */
public final class GatewayFilterOrder {

    public static final int SECURITY_HEADERS = Ordered.HIGHEST_PRECEDENCE;
    public static final int RATE_LIMIT = 10;
    public static final int JWT_AUTHENTICATION = 20;
    public static final int IDEMPOTENCY_PROXY = 30;

    private GatewayFilterOrder() {
    }
}
