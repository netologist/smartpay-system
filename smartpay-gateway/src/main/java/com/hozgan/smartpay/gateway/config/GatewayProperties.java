package com.hozgan.smartpay.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Edge-gateway configuration bound from {@code smartpay.gateway.*}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smartpay.gateway")
public class GatewayProperties {

    /** Distributed idempotency record TTL / retention window. */
    private Idempotency idempotency = new Idempotency();

    /** Outbound reverse-proxy HTTP timeouts. */
    private Http http = new Http();

    /** Per-client-IP token bucket rate limiting. */
    private RateLimit rateLimit = new RateLimit();

    /** Edge JWT (RS256) verification settings. */
    private Security security = new Security();

    /** Browser CORS allow-list (empty = cross-origin browser calls rejected). */
    private Cors cors = new Cors();

    /** Downstream microservice route table (longest-prefix match, path passthrough). */
    private List<Route> routes = new ArrayList<>();

    @Getter
    @Setter
    public static class Idempotency {
        private int ttlHours = 24;
    }

    @Getter
    @Setter
    public static class Http {
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 10000;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int capacity = 100;
        private int refillPerMinute = 100;
        private boolean trustForwardedFor = true;
    }

    @Getter
    @Setter
    public static class Security {
        private boolean enabled = true;
        /** PEM-encoded RS256 public key (inline, or {@code classpath:}/{@code file:} path). */
        private String jwtPublicKey = "";
        /** Optional expected JWT {@code iss} claim. */
        private String jwtIssuer = "";
        private long clockSkewSeconds = 30;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Route {
        /** Ingress path prefix, e.g. {@code /api/v1/payments}. */
        private String prefix;
        /** Downstream base URL, e.g. {@code http://smartpay-payment-service:8082}. */
        private String url;
    }
}
