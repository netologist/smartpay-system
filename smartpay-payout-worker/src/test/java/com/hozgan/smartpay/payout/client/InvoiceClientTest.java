package com.hozgan.smartpay.payout.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.payout.client.dto.InvoiceDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("InvoiceClient — WireMock HTTP Unit Tests")
class InvoiceClientTest {

    private static WireMockServer wireMockServer;
    private InvoiceClient invoiceClient;

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
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();
        tools.jackson.databind.ObjectMapper objectMapper =
                com.hozgan.smartpay.payout.config.PayoutWorkerConfig.createObjectMapper();
        invoiceClient = new InvoiceClient(restClient, objectMapper);
    }

    @Test
    @DisplayName("Successfully fetches invoice by loadId when invoice-service returns 200 OK")
    void shouldFetchInvoiceByLoadIdSuccessfully() {
        String loadIdStr = "LOAD-2026-WM-001";
        LoadId loadId = LoadId.of(loadIdStr);

        String jsonResponse = """
                {
                    "invoiceId": "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
                    "loadId": "LOAD-2026-WM-001",
                    "shipperId": "0191c7a2-9b24-7f11-9a1c-111111111111",
                    "carrierId": "0191c7a2-9b24-7f11-9a1c-222222222222",
                    "vehicleType": "ARTICULATED_LORRY",
                    "mileageMiles": 150.00,
                    "currency": "GBP",
                    "pricing": {
                        "baseAmount": { "amount": "950.00", "currency": "GBP" },
                        "fuelSurcharge": { "amount": "20.00", "currency": "GBP" },
                        "vatAmount": { "amount": "30.00", "currency": "GBP" },
                        "totalAmount": { "amount": "1000.00", "currency": "GBP" }
                    },
                    "status": "EPOD_VERIFIED",
                    "createdAt": "2026-09-05T15:00:00Z"
                }
                """;

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/" + loadIdStr))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        Optional<InvoiceDto> result = invoiceClient.getInvoiceByLoadId(loadId);

        assertThat(result).isPresent();
        InvoiceDto invoice = result.get();
        assertThat(invoice.loadId()).isEqualTo(loadId);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.EPOD_VERIFIED);
        assertThat(invoice.pricing().totalAmount().amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Returns empty Optional when invoice-service returns 404 NOT_FOUND")
    void shouldReturnEmptyOptionalWhenInvoiceNotFound() {
        LoadId loadId = LoadId.of("NON-EXISTENT");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/NON-EXISTENT"))
                .willReturn(aResponse().withStatus(404)));

        Optional<InvoiceDto> result = invoiceClient.getInvoiceByLoadId(loadId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Throws exception when invoice-service returns 500 SERVER_ERROR")
    void shouldThrowExceptionWhenInvoiceServiceFails() {
        LoadId loadId = LoadId.of("LOAD-ERR");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/invoices/by-load/LOAD-ERR"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> invoiceClient.getInvoiceByLoadId(loadId))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Successfully updates invoice status via PUT request")
    void shouldUpdateInvoiceStatusSuccessfully() {
        InvoiceId invoiceId = InvoiceId.of("0191c7a2-9b24-7f11-9a1c-3d842b10a512");

        wireMockServer.stubFor(put(urlEqualTo("/api/v1/invoices/" + invoiceId.value() + "/status?status=FACTORING_APPROVED"))
                .willReturn(aResponse().withStatus(200)));

        invoiceClient.updateInvoiceStatus(invoiceId, InvoiceStatus.FACTORING_APPROVED);

        wireMockServer.verify(WireMock.putRequestedFor(
                urlEqualTo("/api/v1/invoices/" + invoiceId.value() + "/status?status=FACTORING_APPROVED")));
    }
}
