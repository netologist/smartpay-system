package com.hozgan.smartpay.payout.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.payout.client.dto.InvoiceDto;
import com.hozgan.smartpay.payout.config.PayoutWorkerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.kafka.listener.auto-startup=false",
                "resilience4j.circuitbreaker.instances.invoiceService.sliding-window-size=6",
                "resilience4j.circuitbreaker.instances.invoiceService.minimum-number-of-calls=4",
                "resilience4j.circuitbreaker.instances.invoiceService.failure-rate-threshold=50.0",
                "resilience4j.circuitbreaker.instances.invoiceService.wait-duration-in-open-state=2s",
                "resilience4j.retry.instances.invoiceService.max-attempts=2",
                "resilience4j.retry.instances.invoiceService.wait-duration=100ms",
                "resilience4j.retry.instances.invoiceService.retry-exceptions[0]=org.springframework.web.client.HttpServerErrorException",
                "resilience4j.retry.instances.invoiceService.retry-exceptions[1]=java.lang.IllegalStateException"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import({PayoutWorkerConfig.class, InvoiceClientResilienceTest.TestConfig.class})
@DisplayName("InvoiceClient — Resilience4j Circuit Breaker & Retry Tests")
class InvoiceClientResilienceTest {

    private static WireMockServer wireMockServer;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private InvoiceClient invoiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RestClient testInvoiceRestClient() {
            return RestClient.builder()
                    .baseUrl("http://localhost:" + wireMockServer.port())
                    .build();
        }
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("invoiceService");
        cb.reset();
    }

    @Test
    @DisplayName("Circuit Breaker trips to OPEN when downstream failures exceed 50% threshold")
    void shouldTripCircuitBreakerWhenFailureRateThresholdExceeded() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("invoiceService");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        LoadId loadId = LoadId.of("LOAD-FAIL-CB");

        // Downstream service returns 500 server error
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/" + loadId.asString()))
                .willReturn(aResponse().withStatus(500)));

        // Call 4 times (configured minimum-number-of-calls=4)
        for (int i = 0; i < 4; i++) {
            Optional<InvoiceDto> result = invoiceClient.getInvoiceByLoadId(loadId);
            // Fallback returns empty optional gracefully
            assertThat(result).isEmpty();
        }

        // Circuit breaker should now transition to OPEN state
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When in OPEN state, circuit breaker short-circuits calls directly to fallback
        // without making any network call to WireMock!
        int callCountBefore = wireMockServer.getAllServeEvents().size();
        Optional<InvoiceDto> shortCircuitedResult = invoiceClient.getInvoiceByLoadId(loadId);
        assertThat(shortCircuitedResult).isEmpty();

        int callCountAfter = wireMockServer.getAllServeEvents().size();
        assertThat(callCountAfter).isEqualTo(callCountBefore); // Zero additional network calls!
    }

    @Test
    @DisplayName("Retry mechanism retries transient 500 errors before invoking fallback")
    void shouldRetryTransientFailuresBeforeInvokingFallback() {
        LoadId loadId = LoadId.of("LOAD-RETRY-TEST");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/" + loadId.asString()))
                .willReturn(aResponse().withStatus(500)));

        Optional<InvoiceDto> result = invoiceClient.getInvoiceByLoadId(loadId);

        assertThat(result).isEmpty();

        // Configured max-attempts=2 -> should have attempted 2 HTTP calls
        wireMockServer.verify(2, WireMock.getRequestedFor(urlEqualTo("/api/v1/invoices/by-load/" + loadId.asString())));
    }
}
