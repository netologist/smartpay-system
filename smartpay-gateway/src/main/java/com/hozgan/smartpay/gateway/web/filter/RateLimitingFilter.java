package com.hozgan.smartpay.gateway.web.filter;

import com.hozgan.smartpay.gateway.config.GatewayProperties;
import com.hozgan.smartpay.gateway.service.TokenBucketRateLimiter;
import com.hozgan.smartpay.gateway.web.ProblemDetails;
import com.hozgan.smartpay.gateway.web.ProblemDetailsWriter;
import com.hozgan.smartpay.gateway.web.ProblemTypes;
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
 * Token-bucket rate limiter keyed on the resolved client IP (first hop of
 * {@code X-Forwarded-For} when trusted, otherwise the remote address). Runs before JWT
 * verification so brute-force attempts against the edge itself are throttled.
 */
@Component
@Order(GatewayFilterOrder.RATE_LIMIT)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final GatewayProperties properties;
    private final ProblemDetailsWriter problemWriter;
    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitingFilter(GatewayProperties properties, ProblemDetailsWriter problemWriter) {
        this.properties = properties;
        this.problemWriter = problemWriter;
        this.rateLimiter = new TokenBucketRateLimiter(
                properties.getRateLimit().getCapacity(),
                properties.getRateLimit().getRefillPerMinute());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isActuatorPath(request.getRequestURI()) || rateLimiter.tryAcquire(resolveClientIp(request))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit exceeded for client ip={} uri={}", resolveClientIp(request), request.getRequestURI());
        problemWriter.write(response, ProblemDetails.of(ProblemTypes.RATE_LIMIT_EXCEEDED,
                "Too Many Requests", 429, "Rate limit exceeded for this client IP; retry later"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.getRateLimit().isTrustForwardedFor()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }

    static boolean isActuatorPath(String path) {
        return path.startsWith("/actuator");
    }
}
