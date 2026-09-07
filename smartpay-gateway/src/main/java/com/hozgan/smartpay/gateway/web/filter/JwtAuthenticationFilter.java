package com.hozgan.smartpay.gateway.web.filter;

import com.hozgan.smartpay.gateway.config.GatewayProperties;
import com.hozgan.smartpay.gateway.service.JwtPrincipal;
import com.hozgan.smartpay.gateway.service.JwtTokenVerifier;
import com.hozgan.smartpay.gateway.web.ProblemDetails;
import com.hozgan.smartpay.gateway.web.ProblemDetailsWriter;
import com.hozgan.smartpay.gateway.web.ProblemTypes;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Edge RS256 JWT verification. Validates signature, expiry, issuer and extracts the trusted
 * {@code tenant_id} claim, binding it as a request attribute consumed by the idempotency and
 * reverse-proxy filters ({@code X-Tenant-Id} is only ever injected downstream from this
 * verified claim). Actuator endpoints bypass authentication for liveness probes.
 */
@Component
@Order(GatewayFilterOrder.JWT_AUTHENTICATION)
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final GatewayProperties properties;
    private final ProblemDetailsWriter problemWriter;
    private JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationFilter(GatewayProperties properties, ProblemDetailsWriter problemWriter) {
        this.properties = properties;
        this.problemWriter = problemWriter;
    }

    @PostConstruct
    void initVerifier() {
        if (!properties.getSecurity().isEnabled()) {
            log.warn("Edge JWT authentication is DISABLED (smartpay.gateway.security.enabled=false) — "
                    + "intended for local development only");
            return;
        }
        String publicKey = properties.getSecurity().getJwtPublicKey();
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException(
                    "smartpay.gateway.security.jwt-public-key must be configured (PEM) while security is enabled. "
                            + "Set SMARTPAY_GATEWAY_JWT_PUBLIC_KEY or disable smartpay.gateway.security.enabled for local dev.");
        }
        this.tokenVerifier = new JwtTokenVerifier(publicKey,
                properties.getSecurity().getJwtIssuer(),
                properties.getSecurity().getClockSkewSeconds());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!properties.getSecurity().isEnabled() || RateLimitingFilter.isActuatorPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtPrincipal principal = tokenVerifier.verifyAuthorization(request.getHeader("Authorization"));
            GatewayRequestAttributes.bind(request, principal);
            chain.doFilter(request, response);
        } catch (JwtTokenVerifier.JwtVerificationException e) {
            log.debug("Rejected request uri={}: {}", request.getRequestURI(), e.getMessage());
            problemWriter.write(response, ProblemDetails.of(ProblemTypes.UNAUTHORIZED,
                    "Unauthorized", 401, "A valid bearer token is required"));
        }
    }
}
