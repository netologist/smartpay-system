package com.hozgan.smartpay.notification.provider;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Component
public class WebhookProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(WebhookProvider.class);

    private final RestClient restClient;

    public WebhookProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    public ProviderReceipt dispatch(String recipient, RenderedMessage message, Map<String, String> metadata) {
        log.info("Dispatching Webhook to {}: payload='{}'", recipient, message.content());

        String secret = metadata != null && metadata.containsKey("webhookSecret")
                ? metadata.get("webhookSecret")
                : "secret-smartpay-webhook-key";

        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String payload = message.content();
        String signature = computeHmacSha256(secret, timestamp + "." + payload);

        try {
            var response = restClient.post()
                    .uri(recipient)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-SmartPay-Timestamp", timestamp)
                    .header("X-SmartPay-Signature", "sha256=" + signature)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Webhook endpoint returned 5xx server error: status={}", code);
                        throw new NotificationDeliveryException("Webhook endpoint 5xx server error: " + code, code, true);
                    })
                    .onStatus(status -> status.value() == 429, (req, resp) -> {
                        log.warn("Webhook endpoint returned rate limit 429");
                        throw new NotificationDeliveryException("Webhook endpoint rate limit 429", 429, true);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Webhook endpoint returned 4xx client error: status={}", code);
                        throw new NotificationDeliveryException("Webhook endpoint 4xx client error: " + code, code, false);
                    })
                    .toBodilessEntity();

            String deliveryId = "WH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("Webhook dispatched successfully to {}: deliveryId={}", recipient, deliveryId);
            return ProviderReceipt.success(deliveryId);

        } catch (NotificationDeliveryException nde) {
            throw nde;
        } catch (RestClientResponseException rcre) {
            int code = rcre.getStatusCode().value();
            boolean retryable = code == 429 || code >= 500;
            throw new NotificationDeliveryException("Webhook dispatch failed with status: " + code, code, retryable, rcre);
        } catch (Exception e) {
            log.error("Failed to dispatch Webhook to {}: {}", recipient, e.getMessage(), e);
            throw new NotificationDeliveryException("Webhook delivery connection failed: " + e.getMessage(), 503, true, e);
        }
    }

    private String computeHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }
}
