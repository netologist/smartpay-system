package com.hozgan.smartpay.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared harness for gateway integration tests: real PostgreSQL 16 (Testcontainers, gateway
 * schema), a WireMock stub standing in for the downstream payment microservice, a fresh RS256
 * identity key pair, and a tightly bounded rate-limit bucket so tests run quickly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractGatewayIntegrationTest {

    public static final String PAYMENTS_PATH = "/api/v1/payments/initiate";
    public static final String PAYMENT_BODY_97500 = """
            {
              "debtorAccountId": "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
              "creditorAccountId": "0191c7a2-9b24-7f11-9a1c-8e9942a0b124",
              "amountInPence": 97500,
              "currency": "GBP",
              "paymentMethod": "FASTER_PAYMENTS",
              "reference": "PAYOUT-INV-0841"
            }
            """;
    public static final String PAYMENT_BODY_50000 = PAYMENT_BODY_97500.replace("97500", "50000");

    protected static final KeyPair KEY_PAIR = TestJwtKeys.rsaKeyPair();
    private static final AtomicInteger CLIENT_SEQUENCE = new AtomicInteger();

    private static WireMockServer wireMockServer;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeAll
    static void startWireMockOnce() {
        // Started once per JVM: the Spring context is cached across integration classes with the
        // route URL bound to the first dynamic port, so the stub server must never restart.
        synchronized (AbstractGatewayIntegrationTest.class) {
            if (wireMockServer == null) {
                wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
                wireMockServer.start();
                configureFor("127.0.0.1", wireMockServer.port());
            }
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void gatewayOverrides(DynamicPropertyRegistry registry) {
        registry.add("smartpay.gateway.security.enabled", () -> "true");
        registry.add("smartpay.gateway.security.jwt-public-key", () -> TestJwtKeys.publicKeyPem(KEY_PAIR));
        registry.add("smartpay.gateway.rate-limit.capacity", () -> "10");
        registry.add("smartpay.gateway.rate-limit.refill-per-minute", () -> "10");
        registry.add("smartpay.gateway.routes[0].prefix", () -> "/api/v1/payments");
        registry.add("smartpay.gateway.routes[0].url", () -> "http://127.0.0.1:" + wireMockServer.port());
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** A fresh synthetic client IP per test keeps token buckets isolated between tests. */
    protected String clientIp() {
        return "203.0.113." + CLIENT_SEQUENCE.incrementAndGet();
    }

    protected String tenant() {
        return "TENANT-UK-01";
    }

    protected String validToken(String tenantId) {
        return TestJwtKeys.token(KEY_PAIR, "user-" + CLIENT_SEQUENCE.incrementAndGet(), tenantId,
                List.of("ROLE_FINANCE_OPS"), Instant.now().plus(1, ChronoUnit.HOURS), null);
    }

    protected String expiredToken(String tenantId) {
        return TestJwtKeys.token(KEY_PAIR, "user-9", tenantId,
                Instant.now().minus(1, ChronoUnit.HOURS));
    }

    protected HttpResponse<byte[]> send(String method, String path, String bearerToken,
                                        String idempotencyKey, String body, String clientIp) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path));
        builder.header("X-Forwarded-For", clientIp);
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected HttpResponse<byte[]> initiatePayment(String idempotencyKey, String body, String clientIp) throws Exception {
        return send("POST", PAYMENTS_PATH, validToken(tenant()), idempotencyKey, body, clientIp);
    }

    protected void assertProblem(HttpResponse<byte[]> response, int expectedStatus, String expectedType) {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        String payload = new String(response.body());
        assertThat(payload).contains(expectedType);
        assertThat(payload).contains("\"status\":" + expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
    }

    protected String firstHeader(HttpResponse<byte[]> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    protected void assertIdempotencyRow(String tenantId, String key, String expectedStatus) {
        String status = jdbc.queryForObject(
                "SELECT status FROM idempotency_records WHERE tenant_id = ? AND idempotency_key = ?",
                String.class, tenantId, key);
        assertThat(status).isEqualTo(expectedStatus);
    }

    protected String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
