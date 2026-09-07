package com.hozgan.smartpay.gateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class GatewayIdempotencyIntegrationTest extends AbstractGatewayIntegrationTest {

    private static final String CREATED_BODY = """
            {"paymentId":"0191c7c4-8891-7000-84a1-00aa4912fa99","status":"INITIATED",
             "amount":{"currency":"GBP","amount":"975.00","amountInPence":97500},
             "endToEndId":"E2E-SMARTPAY-20260905-9912","createdAt":"2026-09-05T15:00:10.124Z"}
            """;

    private void stubCreated() {
        stubFor(post(urlEqualTo(PAYMENTS_PATH))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CREATED_BODY)));
    }

    // ── AC-1: mandatory header rejection ────────────────────────────────────────────────
    @Test
    void postWithoutIdempotencyKey_returns400Problem() throws Exception {
        stubCreated();

        HttpResponse<byte[]> response = send("POST", PAYMENTS_PATH,
                validToken(tenant()), null, PAYMENT_BODY_97500, clientIp());

        assertProblem(response, 400,
                "https://smartpay.internal/errors/missing-idempotency-key");
        assertThat(new String(response.body()))
                .contains("Mandatory HTTP header 'Idempotency-Key' is missing from the request");
        verify(0, postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    // ── AC-2: new request ingestion (cache miss) ────────────────────────────────────────
    @Test
    void firstRequestWithNewKey_forwardsAndMarksCompleted() throws Exception {
        stubCreated();
        String key = uniqueKey("ac2");

        HttpResponse<byte[]> response = initiatePayment(key, PAYMENT_BODY_97500, clientIp());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("X-Cache")).contains("IDEMPOTENT-MISS");
        assertThat(new String(response.body())).contains("0191c7c4-8891-7000-84a1-00aa4912fa99");
        assertIdempotencyRow(tenant(), key, "COMPLETED");

        verify(exactly(1), postRequestedFor(urlEqualTo(PAYMENTS_PATH))
                .withHeader("X-Tenant-Id", equalTo(tenant()))
                .withHeader("X-User-Id", matching("user-\\d+"))
                .withHeader("Idempotency-Key", equalTo(key))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(PAYMENT_BODY_97500))
                .withoutHeader("Authorization"));
    }

    // ── AC-3: instant replay on duplicate retry (cache hit, downstream bypassed) ────────
    @Test
    void duplicateRetry_servesCachedResponseWithoutContactingDownstream() throws Exception {
        stubCreated();
        String key = uniqueKey("ac3");

        HttpResponse<byte[]> first = initiatePayment(key, PAYMENT_BODY_97500, clientIp());
        HttpResponse<byte[]> second = initiatePayment(key, PAYMENT_BODY_97500, clientIp());

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("X-Cache")).contains("IDEMPOTENT-MISS");
        assertThat(second.headers().firstValue("X-Cache")).contains("IDEMPOTENT-HIT");
        // The replayed body is stored as PostgreSQL JSONB, which normalises whitespace and key
        // order — semantic JSON equality is the contract, not byte identity.
        assertThat(json(second.body())).isEqualTo(json(first.body()));
        assertThat(new String(second.body())).contains("0191c7c4-8891-7000-84a1-00aa4912fa99");
        verify(exactly(1), postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    private static tools.jackson.databind.JsonNode json(byte[] body) throws Exception {
        return new tools.jackson.databind.ObjectMapper().readTree(body);
    }

    // ── AC-4: concurrent conflict detection (409 while in-flight) ───────────────────────
    @Test
    void concurrentRequestWhileProcessing_returns409Conflict() throws Exception {
        stubFor(post(urlEqualTo(PAYMENTS_PATH))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CREATED_BODY)
                        .withFixedDelay(1500)));
        String key = uniqueKey("ac4");
        String client = clientIp();

        CompletableFuture<HttpResponse<byte[]>> first = CompletableFuture.supplyAsync(() -> {
            try {
                return initiatePayment(key, PAYMENT_BODY_97500, client);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(400); // first request has committed its PROCESSING row and is mid-flight

        HttpResponse<byte[]> second = initiatePayment(key, PAYMENT_BODY_97500, client);
        assertProblem(second, 409, "https://smartpay.internal/errors/idempotency-conflict");
        assertThat(new String(second.body())).contains("Concurrent request in progress");

        HttpResponse<byte[]> firstResult = first.get(10, TimeUnit.SECONDS);
        assertThat(firstResult.statusCode()).isEqualTo(201);

        // After completion the same key replays from cache.
        HttpResponse<byte[]> replay = initiatePayment(key, PAYMENT_BODY_97500, client);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("X-Cache")).contains("IDEMPOTENT-HIT");
        assertIdempotencyRow(tenant(), key, "COMPLETED");
        verify(exactly(1), postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    // ── AC-5: tamper rejection (422 on altered payload with reused key) ─────────────────
    @Test
    void alteredPayloadWithCompletedKey_returns422HashMismatch() throws Exception {
        stubCreated();
        String key = uniqueKey("ac5");

        HttpResponse<byte[]> original = initiatePayment(key, PAYMENT_BODY_97500, clientIp());
        assertThat(original.statusCode()).isEqualTo(201);

        HttpResponse<byte[]> tampered = initiatePayment(key, PAYMENT_BODY_50000, clientIp());

        assertProblem(tampered, 422, "https://smartpay.internal/errors/request-hash-mismatch");
        assertThat(new String(tampered.body()))
                .contains("The payload body does not match the original request registered for this idempotency key");
        verify(exactly(1), postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    // ── FAILED downstream attempt releases the key for retry ────────────────────────────
    @Test
    void downstreamFailure_marksFailed_andAllowsSameKeyRetry() throws Exception {
        stubFor(post(urlEqualTo(PAYMENTS_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errorCode\":\"ERR_INTERNAL\"}")));
        String key = uniqueKey("fail-retry");

        HttpResponse<byte[]> failed = initiatePayment(key, PAYMENT_BODY_97500, clientIp());

        assertThat(failed.statusCode()).isEqualTo(500);
        assertThat(new String(failed.body())).contains("ERR_INTERNAL");
        assertIdempotencyRow(tenant(), key, "FAILED");

        stubCreated(); // downstream recovers

        HttpResponse<byte[]> retry = initiatePayment(key, PAYMENT_BODY_97500, clientIp());
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("X-Cache")).contains("IDEMPOTENT-MISS");
        assertIdempotencyRow(tenant(), key, "COMPLETED");

        HttpResponse<byte[]> replay = initiatePayment(key, PAYMENT_BODY_97500, clientIp());
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("X-Cache")).contains("IDEMPOTENT-HIT");

        verify(exactly(2), postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    // ── Non-mutating passthrough proxy (no idempotency) ─────────────────────────────────
    @Test
    void getRequest_isReverseProxiedWithoutIdempotency() throws Exception {
        String statusPath = "/api/v1/payments/0191c7c4-8891-7000-84a1-00aa4912fa99/status";
        stubFor(get(urlEqualTo(statusPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"paymentId\":\"0191c7c4-8891-7000-84a1-00aa4912fa99\",\"status\":\"INITIATED\"}")));

        HttpResponse<byte[]> response = send("GET", statusPath, validToken(tenant()),
                null, null, clientIp());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).contains("\"status\":\"INITIATED\"");
        assertThat(response.headers().firstValue("X-Cache")).isEmpty();
        verify(exactly(1), getRequestedFor(urlEqualTo(statusPath))
                .withHeader("X-Tenant-Id", equalTo(tenant())));
    }

    // ── Unconfigured route is rejected at the perimeter ─────────────────────────────────
    @Test
    void unknownRoute_returns404Problem() throws Exception {
        HttpResponse<byte[]> response = send("POST", "/api/v1/recon/statements/upload",
                validToken(tenant()), uniqueKey("unknown"), "{}", clientIp());

        assertProblem(response, 404, "https://smartpay.internal/errors/route-not-found");
    }

    // ── Blocked path never leaks the caller Authorization header downstream ─────────────
    @Test
    void tenantIsolation_headerInjectedFromVerifiedClaims() throws Exception {
        stubCreated();
        String key = uniqueKey("tenant");
        String otherTenant = "TENANT-UK-99";

        HttpResponse<byte[]> response = send("POST", PAYMENTS_PATH,
                validToken(otherTenant), key, PAYMENT_BODY_97500, clientIp());

        assertThat(response.statusCode()).isEqualTo(201);
        verify(exactly(1), postRequestedFor(urlEqualTo(PAYMENTS_PATH))
                .withHeader("X-Tenant-Id", equalTo(otherTenant)));
        assertIdempotencyRow(otherTenant, key, "COMPLETED");
        // The same key under a different tenant stays isolated (own idempotency slot).
        HttpResponse<byte[]> replayOtherTenant = send("POST", PAYMENTS_PATH,
                validToken(tenant()), key, PAYMENT_BODY_97500, clientIp());
        assertThat(replayOtherTenant.statusCode()).isEqualTo(201);
        assertThat(replayOtherTenant.headers().firstValue("X-Cache")).contains("IDEMPOTENT-MISS");
        verify(exactly(2), postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }
}
