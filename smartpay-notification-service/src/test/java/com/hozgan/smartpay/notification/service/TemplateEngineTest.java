package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.entity.NotificationTemplateEntity;
import com.hozgan.smartpay.notification.exception.TemplateNotFoundException;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateEngine — Dynamic Template Rendering Unit Tests")
class TemplateEngineTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine(templateRepository);
    }

    @Test
    @DisplayName("Renders PAYMENT_SETTLED SMS template with placeholders replaced")
    void shouldRenderPaymentSettledSmsTemplate() {
        NotificationTemplateEntity entity = new NotificationTemplateEntity(
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                null,
                "SmartPay: Payout of {{formattedAmount}} settled to {{carrierName}}. Bank Ref: {{bankRef}}.",
                true
        );

        when(templateRepository.findByTemplateCodeAndChannelAndActiveTrue("PAYMENT_SETTLED", NotificationChannel.SMS))
                .thenReturn(Optional.of(entity));

        Map<String, String> params = Map.of(
                "formattedAmount", "£975.00",
                "carrierName", "FastFreight Ltd",
                "bankRef", "FP-9912"
        );

        RenderedMessage message = templateEngine.render("PAYMENT_SETTLED", NotificationChannel.SMS, params);

        assertThat(message.subject()).isNull();
        assertThat(message.content()).isEqualTo("SmartPay: Payout of £975.00 settled to FastFreight Ltd. Bank Ref: FP-9912.");
    }

    @Test
    @DisplayName("Renders PAYMENT_SETTLED Email template with subject and body replaced")
    void shouldRenderPaymentSettledEmailTemplate() {
        NotificationTemplateEntity entity = new NotificationTemplateEntity(
                "PAYMENT_SETTLED",
                NotificationChannel.EMAIL,
                "SmartPay: Payout Settled - {{bankRef}}",
                "Dear {{carrierName}}, net payout of {{formattedAmount}} settled.",
                true
        );

        when(templateRepository.findByTemplateCodeAndChannelAndActiveTrue("PAYMENT_SETTLED", NotificationChannel.EMAIL))
                .thenReturn(Optional.of(entity));

        Map<String, String> params = Map.of(
                "formattedAmount", "£975.00",
                "carrierName", "FastFreight Ltd",
                "bankRef", "FP-9912"
        );

        RenderedMessage message = templateEngine.render("PAYMENT_SETTLED", NotificationChannel.EMAIL, params);

        assertThat(message.subject()).isEqualTo("SmartPay: Payout Settled - FP-9912");
        assertThat(message.content()).isEqualTo("Dear FastFreight Ltd, net payout of £975.00 settled.");
    }

    @Test
    @DisplayName("Renders fallback template when DB template is not found")
    void shouldRenderFallbackWhenDbTemplateMissing() {
        when(templateRepository.findByTemplateCodeAndChannelAndActiveTrue("PAYMENT_SETTLED", NotificationChannel.SMS))
                .thenReturn(Optional.empty());

        Map<String, String> params = Map.of(
                "formattedAmount", "£975.00",
                "carrierName", "Apex Haulage",
                "bankRef", "FP-1234"
        );

        RenderedMessage message = templateEngine.render("PAYMENT_SETTLED", NotificationChannel.SMS, params);

        assertThat(message.content()).contains("£975.00");
        assertThat(message.content()).contains("Apex Haulage");
        assertThat(message.content()).contains("FP-1234");
    }

    @Test
    @DisplayName("Throws TemplateNotFoundException for unknown template code without fallback")
    void shouldThrowExceptionForUnknownTemplate() {
        when(templateRepository.findByTemplateCodeAndChannelAndActiveTrue("UNKNOWN_CODE", NotificationChannel.SMS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateEngine.render("UNKNOWN_CODE", NotificationChannel.SMS, Map.of()))
                .isInstanceOf(TemplateNotFoundException.class)
                .hasMessageContaining("UNKNOWN_CODE");
    }
}
