package com.hozgan.smartpay.notification.integration;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.TestcontainersConfiguration;
import com.hozgan.smartpay.notification.dto.request.ManualNotificationRequest;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.entity.NotificationTemplateEntity;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.provider.NotificationProvider;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import com.hozgan.smartpay.notification.repository.NotificationTemplateRepository;
import com.hozgan.smartpay.notification.service.NotificationDispatchService;
import com.hozgan.smartpay.notification.service.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.main.allow-bean-definition-overriding=true"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@Import(TestcontainersConfiguration.class)
@DisplayName("NotificationService — Full Stack Database & REST Integration Tests")
class NotificationServiceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private NotificationLogRepository logRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private NotificationDispatchService dispatchService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private List<NotificationProvider> mockProviders;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        logRepository.deleteAll();
    }

    @Test
    @DisplayName("Flyway migrations seeded default notification templates in PostgreSQL")
    void shouldVerifySeededTemplatesInPostgres() {
        Optional<NotificationTemplateEntity> paymentSms =
                templateRepository.findByTemplateCodeAndChannelAndActiveTrue("PAYMENT_SETTLED", NotificationChannel.SMS);
        assertThat(paymentSms).isPresent();
        assertThat(paymentSms.get().getBodyTemplate()).contains("{{formattedAmount}}");

        Optional<NotificationTemplateEntity> invoiceEmail =
                templateRepository.findByTemplateCodeAndChannelAndActiveTrue("INVOICE_ISSUED", NotificationChannel.EMAIL);
        assertThat(invoiceEmail).isPresent();
        assertThat(invoiceEmail.get().getSubject()).contains("{{invoiceId}}");

        Optional<NotificationTemplateEntity> payoutSms =
                templateRepository.findByTemplateCodeAndChannelAndActiveTrue("FACTORING_PAYOUT_APPROVED", NotificationChannel.SMS);
        assertThat(payoutSms).isPresent();
    }

    @Test
    @DisplayName("Persists and updates notification log through dispatch service lifecycle")
    void shouldPersistAndRetrieveNotificationLog() {
        UUID eventId = UUID.randomUUID();
        RenderedMessage message = RenderedMessage.of("SmartPay: Payout £975.00 settled");

        NotificationDispatchResult result = dispatchService.dispatch(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                message,
                Map.of()
        );

        assertThat(result).isNotNull();
        assertThat(result.eventId()).isEqualTo(eventId);

        Optional<NotificationLogEntity> persisted = logRepository.findByEventIdAndChannel(eventId, NotificationChannel.SMS);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getRecipient()).isEqualTo("+447700900123");
        assertThat(persisted.get().getRenderedContent()).isEqualTo("SmartPay: Payout £975.00 settled");
    }

    @Test
    @DisplayName("Database unique constraint enforces idempotency on (event_id, channel)")
    void shouldEnforceDatabaseUniqueConstraintOnEventAndChannel() {
        UUID eventId = UUID.randomUUID();

        NotificationLogEntity first = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "First", NotificationStatus.DISPATCHED
        );
        logRepository.saveAndFlush(first);

        NotificationLogEntity duplicate = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900999",
                "PAYMENT_SETTLED", "Duplicate", NotificationStatus.PENDING
        );

        assertThatThrownBy(() -> logRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("REST API: End-to-end GET /api/v1/notifications/{id} from PostgreSQL")
    void shouldRetrieveNotificationViaRestApi() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationLogEntity entity = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Test content", NotificationStatus.DISPATCHED
        );
        entity.setProviderMessageId("SM-INT-9912");
        entity = logRepository.saveAndFlush(entity);

        mockMvc.perform(get("/api/v1/notifications/{id}", entity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entity.getId().toString()))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.providerMessageId").value("SM-INT-9912"));
    }

    @Test
    @DisplayName("REST API: End-to-end POST /api/v1/notifications/dispatch saves to PostgreSQL")
    void shouldDispatchAndSaveViaRestApi() throws Exception {
        UUID eventId = UUID.randomUUID();

        ManualNotificationRequest request = new ManualNotificationRequest(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                Map.of(
                        "formattedAmount", "£975.00",
                        "carrierName", "Apex Haulage",
                        "bankRef", "FP-8899"
                )
        );

        mockMvc.perform(post("/api/v1/notifications/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.renderedContent").value(org.hamcrest.Matchers.containsString("£975.00")));

        Optional<NotificationLogEntity> persisted = logRepository.findByEventIdAndChannel(eventId, NotificationChannel.SMS);
        assertThat(persisted).isPresent();
    }
}
