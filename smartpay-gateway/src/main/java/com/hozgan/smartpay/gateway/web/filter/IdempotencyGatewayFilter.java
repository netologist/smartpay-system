package com.hozgan.smartpay.gateway.web.filter;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.gateway.config.GatewayProperties;
import com.hozgan.smartpay.gateway.service.DownstreamProxyService;
import com.hozgan.smartpay.gateway.service.IdempotencyService;
import com.hozgan.smartpay.gateway.service.DownstreamProxyService.ProxyResponse;
import com.hozgan.smartpay.gateway.service.IdempotencyService.CachedResponse;
import com.hozgan.smartpay.gateway.web.ProblemDetails;
import com.hozgan.smartpay.gateway.web.ProblemDetailsWriter;
import com.hozgan.smartpay.gateway.web.ProblemTypes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * IdempotencyGatewayFilter — the two-tier SHA-256 idempotency state machine plus reverse-proxy hop.
 *
 * <p>For POST/PUT mutation routes the filter:
 * <ol>
 *   <li>Rejects requests missing the mandatory {@code Idempotency-Key} header (HTTP 400).</li>
 *   <li>Caches the raw request body and computes its SHA-256 fingerprint.</li>
 *   <li>Acquires the {@code (tenantId, key)} slot (see {@link IdempotencyService}):
 *       PROCESSING → 409 conflict, COMPLETED+same hash → instant cache replay bypassing the
 *       downstream, COMPLETED+different hash → 422 tamper rejection, else proceeds.</li>
 *   <li>Proxies to the downstream microservice, then persists the 2xx JSON response as
 *       COMPLETED (X-Cache: IDEMPOTENT-MISS) or releases the slot as FAILED on error.</li>
 * </ol>
 *
 * <p>Non-mutating requests on a routed path are reverse-proxied without idempotency.
 */
@Component
@Order(GatewayFilterOrder.IDEMPOTENCY_PROXY)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyGatewayFilter extends OncePerRequestFilter {

    public static final String X_CACHE = "X-Cache";
    public static final String CACHE_HIT = "IDEMPOTENT-HIT";
    public static final String CACHE_MISS = "IDEMPOTENT-MISS";

    private static final String MISSING_KEY_DETAIL = "Mandatory HTTP header 'Idempotency-Key' is missing from the request";
    private static final String INVALID_KEY_DETAIL = "Idempotency-Key header must be a non-blank value";

    private final GatewayProperties properties;
    private final IdempotencyService idempotencyService;
    private final DownstreamProxyService proxyService;
    private final ProblemDetailsWriter problemWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (RateLimitingFilter.isActuatorPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<GatewayProperties.Route> route = proxyService.resolveRoute(path);
        if (route.isEmpty()) {
            problemWriter.write(response, ProblemDetails.of(ProblemTypes.ROUTE_NOT_FOUND,
                    "Not Found", 404, "No gateway route is configured for " + path));
            return;
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase(Locale.ROOT));
        if (method != HttpMethod.POST && method != HttpMethod.PUT) {
            proxyWithoutIdempotency(request, response, route.get(), method, path);
            return;
        }
        handleMutatingRequest(request, response, route.get(), method, path);
    }

    private void handleMutatingRequest(HttpServletRequest request, HttpServletResponse response,
                                       GatewayProperties.Route route, HttpMethod method, String path)
            throws IOException {
        String idempotencyKeyHeader = request.getHeader("Idempotency-Key");
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            writeBadRequest(response, MISSING_KEY_DETAIL);
            return;
        }
        IdempotencyKey key;
        try {
            key = IdempotencyKey.of(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException e) {
            writeBadRequest(response, INVALID_KEY_DETAIL);
            return;
        }

        TenantId tenantId = GatewayRequestAttributes.tenantId(request);
        if (tenantId == null) {
            tenantId = fallbackTenant(request);
        }

        byte[] body = request.getInputStream().readAllBytes();
        String requestHash = idempotencyService.computeHash(body);

        try {
            Optional<CachedResponse> cached = idempotencyService.acquireLock(tenantId, key, requestHash);
            if (cached.isPresent()) {
                replayCachedResponse(response, cached.get());
                log.info("Idempotency cache hit: tenant={} key={} uri={}", tenantId.value(), key.value(), path);
                return;
            }
            proxyAndSettle(request, response, route, method, path, body,
                    tenantId, key, idempotencyKeyHeader);
        } catch (IdempotencyConflictException e) {
            writeConflict(response, key.value());
        } catch (RequestHashMismatchException e) {
            writeHashMismatch(response);
        }
    }

    private void proxyAndSettle(HttpServletRequest request, HttpServletResponse response,
                                GatewayProperties.Route route, HttpMethod method, String path,
                                byte[] body, TenantId tenantId, IdempotencyKey key,
                                String idempotencyKeyHeader) throws IOException {
        long startNanos = System.nanoTime();
        ProxyResponse downstream;
        try {
            downstream = proxyService.forward(route, method, pathAndQuery(request),
                    body, request.getContentType(), tenantId,
                    GatewayRequestAttributes.userId(request), idempotencyKeyHeader);
        } catch (DownstreamProxyService.DownstreamUnavailableException e) {
            idempotencyService.failIdempotencyRecord(tenantId, key);
            log.error("Downstream unavailable: tenant={} key={} uri={} target={}",
                    tenantId.value(), key.value(), path, route.getUrl());
            problemWriter.write(response, ProblemDetails.of(ProblemTypes.BAD_GATEWAY,
                    "Bad Gateway", 502, "The downstream service could not be reached"));
            return;
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Idempotency cache miss (forwarded): tenant={} key={} uri={} downstreamStatus={} elapsedMs={}",
                tenantId.value(), key.value(), path, downstream.statusCode(), elapsedMs);

        if (cacheableSuccess(downstream)) {
            idempotencyService.completeIdempotencyRecord(tenantId, key,
                    downstream.statusCode(), downstream.textBody());
        } else {
            // Non-2xx or non-JSON: the attempt did not produce a replayable response — release
            // the slot as FAILED so the client may retry the same key.
            idempotencyService.failIdempotencyRecord(tenantId, key);
        }
        writeProxyResponse(response, downstream, CACHE_MISS);
    }

    private boolean cacheableSuccess(ProxyResponse downstream) {
        return downstream.isSuccess() && downstream.isJson() && downstream.body().length > 0;
    }

    private void proxyWithoutIdempotency(HttpServletRequest request, HttpServletResponse response,
                                         GatewayProperties.Route route, HttpMethod method, String path)
            throws IOException {
        TenantId tenantId = GatewayRequestAttributes.tenantId(request);
        if (tenantId == null) {
            tenantId = fallbackTenant(request);
        }
        ProxyResponse downstream;
        try {
            downstream = proxyService.forward(route, method, pathAndQuery(request),
                    readOptionalBody(request), request.getContentType(), tenantId,
                    GatewayRequestAttributes.userId(request), null);
        } catch (DownstreamProxyService.DownstreamUnavailableException e) {
            log.error("Downstream unavailable for non-mutating request: uri={} target={}", path, route.getUrl());
            problemWriter.write(response, ProblemDetails.of(ProblemTypes.BAD_GATEWAY,
                    "Bad Gateway", 502, "The downstream service could not be reached"));
            return;
        }
        writeProxyResponse(response, downstream, null);
    }

    private byte[] readOptionalBody(HttpServletRequest request) throws IOException {
        return request.getContentLength() == 0 ? null : request.getInputStream().readAllBytes();
    }

    private TenantId fallbackTenant(HttpServletRequest request) {
        // Only reachable when edge security is disabled (local dev): honour the caller header.
        String header = request.getHeader("X-Tenant-Id");
        return TenantId.of(header == null || header.isBlank() ? "default" : header.trim());
    }

    private String pathAndQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private void replayCachedResponse(HttpServletResponse response, CachedResponse cached) throws IOException {
        byte[] body = cached.responseBody() == null ? new byte[0]
                : cached.responseBody().getBytes(StandardCharsets.UTF_8);
        response.setStatus(cached.responseCode());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(X_CACHE, CACHE_HIT);
        response.getOutputStream().write(body);
    }

    private void writeProxyResponse(HttpServletResponse response, ProxyResponse downstream, String cacheHeader)
            throws IOException {
        response.setStatus(downstream.statusCode());
        if (downstream.contentType() != null) {
            response.setContentType(downstream.contentType());
        } else if (downstream.body().length > 0) {
            response.setContentType("application/json");
        }
        if (cacheHeader != null) {
            response.setHeader(X_CACHE, cacheHeader);
        }
        if (downstream.body().length > 0) {
            response.getOutputStream().write(downstream.body());
        }
    }

    private void writeBadRequest(HttpServletResponse response, String detail) throws IOException {
        problemWriter.write(response, ProblemDetails.of(ProblemTypes.MISSING_IDEMPOTENCY_KEY,
                "Bad Request", 400, detail));
    }

    private void writeConflict(HttpServletResponse response, String key) throws IOException {
        problemWriter.write(response, ProblemDetails.of(ProblemTypes.IDEMPOTENCY_CONFLICT,
                "Conflict", 409,
                "Concurrent request in progress for idempotency key '" + key + "'"));
    }

    private void writeHashMismatch(HttpServletResponse response) throws IOException {
        problemWriter.write(response, ProblemDetails.of(ProblemTypes.REQUEST_HASH_MISMATCH,
                "Unprocessable Entity", 422,
                "The payload body does not match the original request registered for this idempotency key"));
    }
}
