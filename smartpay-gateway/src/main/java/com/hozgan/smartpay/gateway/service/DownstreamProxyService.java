package com.hozgan.smartpay.gateway.service;

import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.gateway.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Synchronous reverse-proxy hop from the edge gateway to an internal microservice.
 *
 * <p>Forwarding is conservative by design: the gateway injects only trusted identity headers
 * ({@code X-Tenant-Id}, {@code X-User-Id}) derived from the verified JWT, plus the caller's
 * {@code Idempotency-Key} so downstream two-tier idempotency stays aligned on the same key.
 * The caller's {@code Authorization} header is deliberately <em>not</em> forwarded — internal
 * services must never trust an unverified edge token.
 */
@Service
@Slf4j
public class DownstreamProxyService {

    private static final String X_TENANT_ID = "X-Tenant-Id";
    private static final String X_USER_ID = "X-User-Id";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final GatewayProperties properties;
    private RestClient restClient;

    public DownstreamProxyService(GatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initRestClient() {
        GatewayProperties.Http http = properties.getHttp();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(http.getConnectTimeoutMs());
        requestFactory.setReadTimeout(http.getReadTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * Resolves the longest matching configured route prefix for an ingress path.
     */
    public Optional<GatewayProperties.Route> resolveRoute(String path) {
        GatewayProperties.Route best = null;
        for (GatewayProperties.Route route : properties.getRoutes()) {
            String prefix = route.getPrefix();
            if (prefix == null || route.getUrl() == null) {
                continue;
            }
            if (matches(path, prefix) && (best == null || prefix.length() > best.getPrefix().length())) {
                best = route;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean matches(String path, String prefix) {
        if (!prefix.endsWith("/")) {
            // /api/v1/payments matches itself and /api/v1/payments/initiate but not /api/v1/payments2
            return path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?");
        }
        return path.startsWith(prefix);
    }

    /**
     * Forwards the ingress request to {@code route.url + pathAndQuery}, injecting trusted
     * identity headers. All downstream statuses (including 4xx/5xx) are returned untouched.
     *
     * @throws DownstreamUnavailableException when the downstream cannot be reached or times out.
     */
    public ProxyResponse forward(GatewayProperties.Route route, HttpMethod method, String pathAndQuery,
                                 byte[] body, String contentType,
                                 TenantId tenantId, String userId, String idempotencyKey) {
        String target = route.getUrl() + pathAndQuery;
        HttpHeaders headers = new HttpHeaders();
        if (tenantId != null) {
            headers.set(X_TENANT_ID, tenantId.value());
        }
        if (userId != null) {
            headers.set(X_USER_ID, userId);
        }
        if (idempotencyKey != null) {
            headers.set(IDEMPOTENCY_KEY, idempotencyKey);
        }
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (contentType != null) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        }

        try {
            ResponseEntity<byte[]> response = buildRequest(method, target, headers, body)
                    .retrieve()
                    .toEntity(byte[].class);
            return new ProxyResponse(response.getStatusCode().value(),
                    Optional.ofNullable(response.getHeaders().getContentType())
                            .map(MediaType::toString).orElse(null),
                    response.getBody() == null ? new byte[0] : response.getBody());
        } catch (HttpStatusCodeException e) {
            // Downstream answered with an error status: pass it through untouched (never cached).
            byte[] errorBody = e.getResponseBodyAsByteArray();
            return new ProxyResponse(e.getStatusCode().value(),
                    e.getResponseHeaders() == null ? null
                            : Optional.ofNullable(e.getResponseHeaders().getContentType())
                                    .map(MediaType::toString).orElse(null),
                    errorBody == null ? new byte[0] : errorBody);
        } catch (RestClientException e) {
            log.warn("Downstream call failed for {}: {}", target, e.getMessage());
            throw new DownstreamUnavailableException(target, e);
        }
    }

    private RestClient.RequestBodySpec buildRequest(HttpMethod method, String target,
                                                    HttpHeaders headers, byte[] body) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(target).headers(h -> h.putAll(headers));
        return body == null ? spec : spec.body(body);
    }

    public record ProxyResponse(int statusCode, String contentType, byte[] body) {

        public String textBody() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isJson() {
            return contentType != null && contentType.toLowerCase().contains("json");
        }
    }

    /** Downstream unreachable, connection refused, or read/connect timeout. Maps to HTTP 502. */
    public static final class DownstreamUnavailableException extends RuntimeException {
        public DownstreamUnavailableException(String target, Throwable cause) {
            super("Downstream service unreachable at " + target, cause);
        }
    }
}
