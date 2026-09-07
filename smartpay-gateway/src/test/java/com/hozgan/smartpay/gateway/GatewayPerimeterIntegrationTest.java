package com.hozgan.smartpay.gateway;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class GatewayPerimeterIntegrationTest extends AbstractGatewayIntegrationTest {

    private void stubCreated() {
        stubFor(post(urlEqualTo(PAYMENTS_PATH))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"paymentId\":\"0191c7c4-8891-7000-84a1-00aa4912fa99\",\"status\":\"INITIATED\"}")));
    }

    @Test
    void actuatorHealth_isOpenWithoutAuthentication() throws Exception {
        HttpResponse<byte[]> response = send("GET", "/actuator/health", null, null, null, clientIp());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void missingBearerToken_returns401Problem() throws Exception {
        HttpResponse<byte[]> response = send("POST", PAYMENTS_PATH,
                null, uniqueKey("no-auth"), PAYMENT_BODY_97500, clientIp());

        assertProblem(response, 401, "https://smartpay.internal/errors/unauthorized");
    }

    @Test
    void malformedBearerToken_returns401Problem() throws Exception {
        HttpResponse<byte[]> response = send("POST", PAYMENTS_PATH,
                "not-a-jwt", uniqueKey("bad-token"), PAYMENT_BODY_97500, clientIp());

        assertProblem(response, 401, "https://smartpay.internal/errors/unauthorized");
    }

    @Test
    void expiredBearerToken_returns401Problem() throws Exception {
        HttpResponse<byte[]> response = send("POST", PAYMENTS_PATH,
                expiredToken(tenant()), uniqueKey("expired"), PAYMENT_BODY_97500, clientIp());

        assertProblem(response, 401, "https://smartpay.internal/errors/unauthorized");
    }

    @Test
    void securityHeaders_areInjectedOnEveryResponse() throws Exception {
        stubCreated();
        HttpResponse<byte[]> response = initiatePayment(uniqueKey("headers"), PAYMENT_BODY_97500, clientIp());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(firstHeader(response, "Content-Security-Policy")).contains("default-src 'none'");
        assertThat(firstHeader(response, "X-Content-Type-Options")).contains("nosniff");
        assertThat(firstHeader(response, "X-Frame-Options")).contains("DENY");
    }

    @Test
    void corsPreflightFromAllowedOrigin_isShortCircuited() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(baseUrl() + PAYMENTS_PATH))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("X-Forwarded-For", clientIp())
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(firstHeader(response, "Access-Control-Allow-Origin")).contains("http://localhost:3000");
        assertThat(firstHeader(response, "Access-Control-Allow-Methods")).contains("POST");
        verify(0, postRequestedFor(urlEqualTo(PAYMENTS_PATH)));
    }

    @Test
    void corsPreflightFromUnknownOrigin_isForbidden() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(baseUrl() + PAYMENTS_PATH))
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .header("X-Forwarded-For", clientIp())
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> response = java.net.http.HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertProblem(response, 403, "https://smartpay.internal/errors/cors-origin-forbidden");
    }

    @Test
    void exceedingRateLimit_returns429TooManyRequests() throws Exception {
        String ip = clientIp(); // dedicated bucket for this test
        int allowed = 0;
        HttpResponse<byte[]> last = null;
        for (int i = 0; i < 11; i++) {
            last = send("POST", PAYMENTS_PATH, null, uniqueKey("rate"), PAYMENT_BODY_97500, ip);
            if (last.statusCode() == 429) {
                break;
            }
            allowed++;
        }

        assertThat(allowed).isEqualTo(10); // capacity granted to a fresh bucket
        assertThat(last).isNotNull();
        assertProblem(last, 429, "https://smartpay.internal/errors/rate-limit-exceeded");
    }

    @Test
    void authenticatedRequest_passThroughRateLimitAndReachDownstream() throws Exception {
        stubCreated();
        List<String> keys = List.of("rl-ok-1", "rl-ok-2");

        for (String key : keys) {
            HttpResponse<byte[]> response = initiatePayment(key, PAYMENT_BODY_97500, clientIp());
            assertThat(response.statusCode()).isEqualTo(201);
        }
    }
}
