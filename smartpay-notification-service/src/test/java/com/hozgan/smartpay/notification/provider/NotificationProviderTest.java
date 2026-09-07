package com.hozgan.smartpay.notification.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.config.NotificationProperties;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Notification Providers — Twilio, SendGrid, Webhook WireMock Tests")
class NotificationProviderTest {

    private static WireMockServer wireMockServer;

    private TwilioSmsProvider twilioProvider;
    private SendGridEmailProvider sendGridProvider;
    private WebhookProvider webhookProvider;

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

        String baseUrl = wireMockServer.baseUrl();

        NotificationProperties.TwilioProperties twilioProps = new NotificationProperties.TwilioProperties(
                baseUrl, "AC123456", "token123", "+447700900000"
        );
        NotificationProperties.SendGridProperties sendgridProps = new NotificationProperties.SendGridProperties(
                baseUrl, "SG.TEST_KEY", "notifications@smartpay.io"
        );
        NotificationProperties.WebhookProperties webhookProps = new NotificationProperties.WebhookProperties(
                5, 3
        );

        NotificationProperties properties = new NotificationProperties(
                "test-group",
                new NotificationProperties.Topics(null, null, null, null),
                new NotificationProperties.Providers(twilioProps, sendgridProps, webhookProps)
        );

        RestClient.Builder builder = RestClient.builder();
        twilioProvider = new TwilioSmsProvider(builder, properties);
        sendGridProvider = new SendGridEmailProvider(builder, properties);
        webhookProvider = new WebhookProvider(builder);
    }

    @Test
    @DisplayName("TwilioSmsProvider dispatches successfully on 200/201 response")
    void shouldDispatchTwilioSmsSuccessfully() {
        wireMockServer.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\": \"SM9876543210\", \"status\": \"queued\"}")));

        RenderedMessage message = RenderedMessage.of("SmartPay: Payout £975.00 settled.");
        ProviderReceipt receipt = twilioProvider.dispatch("+447700900123", message, Map.of());

        assertThat(receipt.status().name()).isEqualTo("DISPATCHED");
        assertThat(receipt.providerMessageId()).isEqualTo("SM9876543210");
        assertThat(twilioProvider.channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("TwilioSmsProvider throws retryable NotificationDeliveryException on 500 error")
    void shouldThrowRetryableExceptionOnTwilio500() {
        wireMockServer.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        RenderedMessage message = RenderedMessage.of("SmartPay: Payout £975.00 settled.");

        assertThatThrownBy(() -> twilioProvider.dispatch("+447700900123", message, Map.of()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> {
                    NotificationDeliveryException nde = (NotificationDeliveryException) ex;
                    assertThat(nde.getStatusCode()).isEqualTo(500);
                    assertThat(nde.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("SendGridEmailProvider dispatches successfully on 202 response")
    void shouldDispatchSendGridEmailSuccessfully() {
        wireMockServer.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse()
                        .withStatus(202)
                        .withHeader("X-Message-Id", "SG-MSG-556677")));

        RenderedMessage message = RenderedMessage.of("Subject", "Body content");
        ProviderReceipt receipt = sendGridProvider.dispatch("billing@shipper.com", message, Map.of());

        assertThat(receipt.status().name()).isEqualTo("DISPATCHED");
        assertThat(receipt.providerMessageId()).isEqualTo("SG-MSG-556677");
        assertThat(sendGridProvider.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("SendGridEmailProvider throws retryable exception on 429 rate limit")
    void shouldThrowRetryableExceptionOnSendGrid429() {
        wireMockServer.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("Rate limit exceeded")));

        RenderedMessage message = RenderedMessage.of("Subject", "Body content");

        assertThatThrownBy(() -> sendGridProvider.dispatch("billing@shipper.com", message, Map.of()))
                .isInstanceOf(NotificationDeliveryException.class)
                .satisfies(ex -> {
                    NotificationDeliveryException nde = (NotificationDeliveryException) ex;
                    assertThat(nde.getStatusCode()).isEqualTo(429);
                    assertThat(nde.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("WebhookProvider dispatches signed HMAC-SHA256 payload and headers")
    void shouldDispatchSignedWebhookSuccessfully() {
        wireMockServer.stubFor(post(urlEqualTo("/webhook/test"))
                .willReturn(aResponse()
                        .withStatus(200)));

        String webhookUrl = wireMockServer.baseUrl() + "/webhook/test";
        RenderedMessage message = RenderedMessage.of("{\"event\":\"PAYMENT_SETTLED\",\"amount\":\"£975.00\"}");

        ProviderReceipt receipt = webhookProvider.dispatch(
                webhookUrl, message, Map.of("webhookSecret", "test-secret-key-123")
        );

        assertThat(receipt.status().name()).isEqualTo("DISPATCHED");
        assertThat(receipt.providerMessageId()).startsWith("WH-");
        assertThat(webhookProvider.channel()).isEqualTo(NotificationChannel.WEBHOOK);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/webhook/test"))
                .withHeader("X-SmartPay-Timestamp", matching("[0-9]+"))
                .withHeader("X-SmartPay-Signature", matching("sha256=[a-f0-9]{64}")));
    }
}
