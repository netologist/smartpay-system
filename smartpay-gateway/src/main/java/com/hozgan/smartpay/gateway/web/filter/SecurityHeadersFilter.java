package com.hozgan.smartpay.gateway.web.filter;

import com.hozgan.smartpay.gateway.config.GatewayProperties;
import com.hozgan.smartpay.gateway.web.ProblemDetails;
import com.hozgan.smartpay.gateway.web.ProblemDetailsWriter;
import com.hozgan.smartpay.gateway.web.ProblemTypes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Outermost edge filter: injects strict browser security headers, enforces the CORS allow-list,
 * and short-circuits cross-origin preflight requests before authentication or rate limiting.
 */
@Component
@Order(GatewayFilterOrder.SECURITY_HEADERS)
@RequiredArgsConstructor
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    private final GatewayProperties properties;
    private final ProblemDetailsWriter problemWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        boolean allowedOrigin = origin != null && isAllowedOrigin(origin);

        // Cross-origin preflight never carries credentials; answer it at the perimeter.
        if (HttpMethod.OPTIONS.matches(request.getMethod()) && origin != null) {
            if (!allowedOrigin) {
                problemWriter.write(response, ProblemDetails.of(ProblemTypes.FORBIDDEN_ORIGIN,
                        "Forbidden", 403, "Origin is not allowed by the gateway CORS policy"));
                return;
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Idempotency-Key");
            response.setHeader("Access-Control-Max-Age", "3600");
            return;
        }

        applySecurityHeaders(response);
        if (allowedOrigin) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
        }
        chain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
    }

    private boolean isAllowedOrigin(String origin) {
        List<String> allowed = properties.getCors().getAllowedOrigins();
        return allowed.contains("*") || allowed.contains(origin);
    }
}
