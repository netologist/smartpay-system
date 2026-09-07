package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.entity.NotificationTemplateEntity;
import com.hozgan.smartpay.notification.exception.TemplateNotFoundException;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    private final NotificationTemplateRepository templateRepository;

    public TemplateEngine(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public RenderedMessage render(String templateCode, NotificationChannel channel, Map<String, String> parameters) {
        Objects.requireNonNull(templateCode, "templateCode cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        Map<String, String> safeParams = parameters != null ? parameters : Map.of();

        Optional<NotificationTemplateEntity> templateOpt =
                templateRepository.findByTemplateCodeAndChannelAndActiveTrue(templateCode, channel);

        if (templateOpt.isEmpty()) {
            log.warn("Template not found in DB for code={}, channel={}. Attempting default fallback.",
                    templateCode, channel);
            return renderFallback(templateCode, channel, safeParams);
        }

        NotificationTemplateEntity entity = templateOpt.get();
        String renderedSubject = entity.getSubject() != null
                ? replacePlaceholders(entity.getSubject(), safeParams)
                : null;
        String renderedBody = replacePlaceholders(entity.getBodyTemplate(), safeParams);

        return new RenderedMessage(renderedSubject, renderedBody);
    }

    private String replacePlaceholders(String text, Map<String, String> parameters) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }

    private RenderedMessage renderFallback(String templateCode, NotificationChannel channel, Map<String, String> params) {
        String amount = params.getOrDefault("formattedAmount", "£0.00");
        String carrier = params.getOrDefault("carrierName", "Carrier");
        String bankRef = params.getOrDefault("bankRef", "N/A");
        String invoiceId = params.getOrDefault("invoiceId", "N/A");

        return switch (templateCode) {
            case "PAYMENT_SETTLED" -> switch (channel) {
                case SMS -> RenderedMessage.of("SmartPay: Payout of " + amount + " settled to " + carrier + ". Bank Ref: " + bankRef + ".");
                case EMAIL -> RenderedMessage.of("SmartPay: Payout Settled - " + bankRef,
                        "Dear " + carrier + ",\n\nYour net payout of " + amount + " has been settled. Ref: " + bankRef);
                case WEBHOOK -> RenderedMessage.of("{\"event\":\"PAYMENT_SETTLED\",\"amount\":\"" + amount + "\",\"bankRef\":\"" + bankRef + "\"}");
            };
            case "INVOICE_ISSUED" -> switch (channel) {
                case SMS -> RenderedMessage.of("SmartPay: Invoice " + invoiceId + " issued for " + amount + ".");
                case EMAIL -> RenderedMessage.of("SmartPay: Invoice Issued - " + invoiceId,
                        "Invoice " + invoiceId + " issued for total gross " + amount + ".");
                case WEBHOOK -> RenderedMessage.of("{\"event\":\"INVOICE_ISSUED\",\"invoiceId\":\"" + invoiceId + "\",\"amount\":\"" + amount + "\"}");
            };
            case "FACTORING_PAYOUT_APPROVED" -> switch (channel) {
                case SMS -> RenderedMessage.of("SmartPay: Factoring payout of " + amount + " approved for invoice " + invoiceId + ".");
                case EMAIL -> RenderedMessage.of("SmartPay: Factoring Payout Approved - " + invoiceId,
                        "Factoring payout of " + amount + " approved for invoice " + invoiceId + ".");
                case WEBHOOK -> RenderedMessage.of("{\"event\":\"FACTORING_PAYOUT_APPROVED\",\"invoiceId\":\"" + invoiceId + "\",\"amount\":\"" + amount + "\"}");
            };
            default -> throw new TemplateNotFoundException(templateCode, channel.name());
        };
    }
}
