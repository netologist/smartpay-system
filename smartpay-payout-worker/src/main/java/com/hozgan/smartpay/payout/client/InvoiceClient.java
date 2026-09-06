package com.hozgan.smartpay.payout.client;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.payout.client.dto.InvoiceDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class InvoiceClient {

    private static final Logger log = LoggerFactory.getLogger(InvoiceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InvoiceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches invoice details by load ID.
     * Protected by Resilience4j Circuit Breaker, Rate Limiter, and Retry.
     * Retries transient failures before invoking fallback.
     *
     * @param loadId load identifier
     * @return Optional of InvoiceDto, or empty if 404
     */
    @CircuitBreaker(name = "invoiceService")
    @Retry(name = "invoiceService", fallbackMethod = "getInvoiceByLoadIdFallback")
    @RateLimiter(name = "invoiceService")
    public Optional<InvoiceDto> getInvoiceByLoadId(LoadId loadId) {
        log.debug("Calling invoice-service for loadId: {}", loadId);
        return restClient.get()
                .uri("/api/v1/invoices/by-load/{loadId}", loadId.asString())
                .exchange((request, response) -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        byte[] bytes = response.getBody().readAllBytes();
                        InvoiceDto invoice = objectMapper.readValue(bytes, InvoiceDto.class);
                        return Optional.ofNullable(invoice);
                    } else if (response.getStatusCode().value() == 404) {
                        log.warn("Invoice not found in invoice-service for loadId: {}", loadId);
                        return Optional.empty();
                    } else if (response.getStatusCode().is5xxServerError()) {
                        log.error("Server error from invoice-service for loadId: {}, status: {}",
                                loadId, response.getStatusCode());
                        throw new HttpServerErrorException(response.getStatusCode(),
                                "Invoice service returned server error: " + response.getStatusCode());
                    } else {
                        log.error("Failed to fetch invoice for loadId: {}, status: {}",
                                loadId, response.getStatusCode());
                        throw new IllegalStateException("Invoice service returned error status: " + response.getStatusCode());
                    }
                });
    }

    /**
     * Circuit breaker / Retry fallback method when invoice-service is unreachable or circuit is OPEN.
     */
    public Optional<InvoiceDto> getInvoiceByLoadIdFallback(LoadId loadId, Throwable t) {
        log.warn("Invoice service circuit breaker open or call failed for loadId={}: {}", loadId, t.getMessage());
        return Optional.empty();
    }

    /**
     * Updates invoice status (e.g. FACTORING_APPROVED).
     * Protected by Resilience4j Circuit Breaker and Retry.
     *
     * @param invoiceId invoice identifier
     * @param newStatus target invoice status
     */
    @CircuitBreaker(name = "invoiceService")
    @Retry(name = "invoiceService")
    public void updateInvoiceStatus(InvoiceId invoiceId, InvoiceStatus newStatus) {
        log.info("Updating invoice {} status to {} via invoice-service", invoiceId, newStatus);
        restClient.put()
                .uri("/api/v1/invoices/{id}/status?status={status}", invoiceId.value(), newStatus.name())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    log.error("Failed to update invoice {} status to {}. Status: {}",
                            invoiceId, newStatus, response.getStatusCode());
                    if (response.getStatusCode().is5xxServerError()) {
                        throw new HttpServerErrorException(response.getStatusCode(),
                                "Failed to update invoice status: " + response.getStatusCode());
                    }
                    throw new IllegalStateException("Failed to update invoice status: " + response.getStatusCode());
                })
                .toBodilessEntity();
    }
}
