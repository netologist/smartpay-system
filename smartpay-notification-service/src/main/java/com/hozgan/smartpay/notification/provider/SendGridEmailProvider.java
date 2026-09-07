package com.hozgan.smartpay.notification.provider;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.config.NotificationProperties;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
public class SendGridEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailProvider.class);

    private final RestClient restClient;
    private final NotificationProperties.SendGridProperties sendGridProps;

    public SendGridEmailProvider(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        this.sendGridProps = properties.providers().sendgrid();
        this.restClient = restClientBuilder
                .baseUrl(sendGridProps.baseUrl())
                .build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderReceipt dispatch(String recipient, RenderedMessage message, Map<String, String> metadata) {
        log.info("Dispatching Email via SendGrid to {}: subject='{}'", recipient, message.subject());

        String subject = message.subject() != null ? message.subject() : "SmartPay Notification";
        String escapedSubject = escapeJson(subject);
        String escapedContent = escapeJson(message.content());

        String jsonPayload = """
                {
                  "personalizations": [{"to": [{"email": "%s"}]}],
                  "from": {"email": "%s"},
                  "subject": "%s",
                  "content": [{"type": "text/plain", "value": "%s"}]
                }
                """.formatted(recipient, sendGridProps.fromEmail(), escapedSubject, escapedContent);

        try {
            var response = restClient.post()
                    .uri("/v3/mail/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sendGridProps.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("SendGrid returned 5xx server error: status={}", code);
                        throw new NotificationDeliveryException("SendGrid email provider 5xx server error: " + code, code, true);
                    })
                    .onStatus(status -> status.value() == 429, (req, resp) -> {
                        log.warn("SendGrid rate limit exceeded (429)");
                        throw new NotificationDeliveryException("SendGrid email rate limit exceeded (429)", 429, true);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("SendGrid returned client error: status={}", code);
                        throw new NotificationDeliveryException("SendGrid email client error: " + code, code, false);
                    })
                    .toBodilessEntity();

            String providerMsgId = "SG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            if (response.getHeaders().getFirst("X-Message-Id") != null) {
                providerMsgId = response.getHeaders().getFirst("X-Message-Id");
            }

            log.info("SendGrid email dispatched successfully: msgId={}", providerMsgId);
            return ProviderReceipt.success(providerMsgId);

        } catch (NotificationDeliveryException nde) {
            throw nde;
        } catch (RestClientResponseException rcre) {
            int code = rcre.getStatusCode().value();
            boolean retryable = code == 429 || code >= 500;
            throw new NotificationDeliveryException("SendGrid email dispatch failed with status: " + code, code, retryable, rcre);
        } catch (Exception e) {
            log.error("Failed to dispatch SendGrid email to {}: {}", recipient, e.getMessage(), e);
            throw new NotificationDeliveryException("SendGrid email connection failed: " + e.getMessage(), 503, true, e);
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
