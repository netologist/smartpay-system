package com.hozgan.smartpay.notification.provider;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.config.NotificationProperties;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
public class TwilioSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsProvider.class);

    private final RestClient restClient;
    private final NotificationProperties.TwilioProperties twilioProps;

    public TwilioSmsProvider(RestClient.Builder restClientBuilder, NotificationProperties properties) {
        this.twilioProps = properties.providers().twilio();
        this.restClient = restClientBuilder
                .baseUrl(twilioProps.baseUrl())
                .build();
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public ProviderReceipt dispatch(String recipient, RenderedMessage message, Map<String, String> metadata) {
        log.info("Dispatching SMS via Twilio to {}: content='{}'", recipient, message.content());

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("To", recipient);
        formData.add("From", twilioProps.fromNumber());
        formData.add("Body", message.content());

        String path = "/2010-04-01/Accounts/" + twilioProps.accountSid() + "/Messages.json";

        try {
            var response = restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBasicAuth(twilioProps.accountSid(), twilioProps.authToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Twilio returned 5xx server error: status={}", code);
                        throw new NotificationDeliveryException("Twilio SMS provider 5xx server error: " + code, code, true);
                    })
                    .onStatus(status -> status.value() == 429, (req, resp) -> {
                        log.warn("Twilio rate limit exceeded (429)");
                        throw new NotificationDeliveryException("Twilio SMS rate limit exceeded (429)", 429, true);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        log.error("Twilio returned client error: status={}", code);
                        throw new NotificationDeliveryException("Twilio SMS client error: " + code, code, false);
                    })
                    .body(String.class);

            String providerMsgId = extractSid(response);
            log.info("Twilio SMS dispatched successfully: sid={}", providerMsgId);
            return ProviderReceipt.success(providerMsgId);

        } catch (NotificationDeliveryException nde) {
            throw nde;
        } catch (RestClientResponseException rcre) {
            int code = rcre.getStatusCode().value();
            boolean retryable = code == 429 || code >= 500;
            throw new NotificationDeliveryException("Twilio SMS dispatch failed with status: " + code, code, retryable, rcre);
        } catch (Exception e) {
            log.error("Failed to dispatch Twilio SMS to {}: {}", recipient, e.getMessage(), e);
            throw new NotificationDeliveryException("Twilio SMS connection failed: " + e.getMessage(), 503, true, e);
        }
    }

    private String extractSid(String responseBody) {
        if (responseBody != null && responseBody.contains("\"sid\":")) {
            int start = responseBody.indexOf("\"sid\":") + 6;
            int quoteStart = responseBody.indexOf("\"", start);
            if (quoteStart != -1) {
                int quoteEnd = responseBody.indexOf("\"", quoteStart + 1);
                if (quoteEnd != -1) {
                    return responseBody.substring(quoteStart + 1, quoteEnd);
                }
            }
        }
        return "SM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
