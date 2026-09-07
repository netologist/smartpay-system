package com.hozgan.smartpay.notification.web;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.dto.request.ManualNotificationRequest;
import com.hozgan.smartpay.notification.dto.response.NotificationLogResponse;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.mapper.NotificationMapper;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import com.hozgan.smartpay.notification.service.NotificationDispatchService;
import com.hozgan.smartpay.notification.service.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController — Web API Unit Tests")
class NotificationControllerTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationDispatchService dispatchService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private NotificationMapper mapper;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(
                logRepository, dispatchService, templateEngine, mapper
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        this.objectMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} returns 200 when found")
    void shouldReturnNotificationById() throws Exception {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        NotificationLogEntity entity = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Content", NotificationStatus.DISPATCHED
        );
        entity.setId(id);

        NotificationLogResponse response = new NotificationLogResponse(
                id, eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Content", NotificationStatus.DISPATCHED,
                "SM-123", 0, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(logRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} returns 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(logRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/notifications/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/notifications?eventId=... returns list of notifications")
    void shouldReturnNotificationsByEventId() throws Exception {
        UUID eventId = UUID.randomUUID();
        NotificationLogEntity entity = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Content", NotificationStatus.DISPATCHED
        );

        NotificationLogResponse response = new NotificationLogResponse(
                entity.getId(), eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Content", NotificationStatus.DISPATCHED,
                "SM-123", 0, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(logRepository.findByEventId(eventId)).thenReturn(List.of(entity));
        when(mapper.toResponseList(List.of(entity))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/notifications").param("eventId", eventId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/dispatch triggers manual dispatch")
    void shouldTriggerManualDispatch() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        ManualNotificationRequest request = new ManualNotificationRequest(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                Map.of("formattedAmount", "£975.00")
        );

        RenderedMessage rendered = RenderedMessage.of("SmartPay: Payout £975.00");
        when(templateEngine.render(eq("PAYMENT_SETTLED"), eq(NotificationChannel.SMS), any()))
                .thenReturn(rendered);

        NotificationDispatchResult dispatchResult = new NotificationDispatchResult(
                notifId, eventId, NotificationChannel.SMS, "+447700900123",
                NotificationStatus.DISPATCHED, "SM-9912", null
        );
        when(dispatchService.dispatch(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(dispatchResult);

        NotificationLogEntity saved = new NotificationLogEntity();
        saved.setId(notifId);

        NotificationLogResponse response = new NotificationLogResponse(
                notifId, eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "SmartPay: Payout £975.00", NotificationStatus.DISPATCHED,
                "SM-9912", 0, null, Instant.now(), Instant.now(), Instant.now()
        );

        when(logRepository.findById(notifId)).thenReturn(Optional.of(saved));
        when(mapper.toResponse(saved)).thenReturn(response);

        mockMvc.perform(post("/api/v1/notifications/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notifId.toString()))
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/dispatch validates required fields")
    void shouldValidateRequiredFields() throws Exception {
        String invalidJson = "{\"parameters\": {}}";

        mockMvc.perform(post("/api/v1/notifications/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"));
    }
}
