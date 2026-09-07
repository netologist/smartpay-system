package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.DeadLetterNotificationPayload;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.provider.NotificationProvider;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchService — Unit & Resilience Tests")
class NotificationDispatchServiceTest {

    @Mock
    private NotificationProvider smsProvider;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private NotificationDlqPublisher dlqPublisher;

    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        when(smsProvider.channel()).thenReturn(NotificationChannel.SMS);
        dispatchService = new NotificationDispatchService(
                List.of(smsProvider),
                logRepository,
                dlqPublisher,
                RetryRegistry.ofDefaults(),
                CircuitBreakerRegistry.ofDefaults()
        );
    }

    @Test
    @DisplayName("Successfully dispatches notification and updates status to DISPATCHED")
    void shouldDispatchSuccessfully() {
        UUID eventId = UUID.randomUUID();
        RenderedMessage message = RenderedMessage.of("SmartPay: Payout £975.00 settled");

        when(logRepository.findByEventIdAndChannel(eventId, NotificationChannel.SMS))
                .thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(NotificationLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(smsProvider.dispatch(eq("+447700900123"), eq(message), any()))
                .thenReturn(ProviderReceipt.success("SM-9912"));

        NotificationDispatchResult result = dispatchService.dispatch(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                message,
                Map.of()
        );

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.status()).isEqualTo(NotificationStatus.DISPATCHED);
        assertThat(result.providerMessageId()).isEqualTo("SM-9912");

        verify(smsProvider, times(1)).dispatch(any(), any(), any());
        verify(dlqPublisher, never()).publishToDlq(any());
    }

    @Test
    @DisplayName("Returns DISPATCHED immediately if log already marked DISPATCHED (Idempotency)")
    void shouldReturnDispatchedIfAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        NotificationLogEntity existing = new NotificationLogEntity(
                eventId, "PAYMENT_SETTLED", NotificationChannel.SMS, "+447700900123",
                "PAYMENT_SETTLED", "Already sent", NotificationStatus.DISPATCHED
        );
        existing.setProviderMessageId("SM-EXISTING");

        when(logRepository.findByEventIdAndChannel(eventId, NotificationChannel.SMS))
                .thenReturn(Optional.of(existing));

        NotificationDispatchResult result = dispatchService.dispatch(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                RenderedMessage.of("Already sent"),
                Map.of()
        );

        assertThat(result.status()).isEqualTo(NotificationStatus.DISPATCHED);
        assertThat(result.providerMessageId()).isEqualTo("SM-EXISTING");
        verify(smsProvider, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("Retries on 5xx outage, marks DEAD_LETTERED, and forwards to DLQ after retries exhausted (AC-3)")
    void shouldRetryAndForwardToDlqOnExhaustion() {
        UUID eventId = UUID.randomUUID();
        RenderedMessage message = RenderedMessage.of("SmartPay: Payout £975.00 settled");

        when(logRepository.findByEventIdAndChannel(eventId, NotificationChannel.SMS))
                .thenReturn(Optional.empty());
        when(logRepository.saveAndFlush(any(NotificationLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Simulate Twilio 500 Outage
        when(smsProvider.dispatch(eq("+447700900123"), eq(message), any()))
                .thenThrow(new NotificationDeliveryException("Twilio HTTP 500 Internal Server Error", 500, true));

        NotificationDispatchResult result = dispatchService.dispatch(
                eventId,
                "PAYMENT_SETTLED",
                NotificationChannel.SMS,
                "+447700900123",
                "PAYMENT_SETTLED",
                message,
                Map.of()
        );

        assertThat(result.status()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        assertThat(result.errorMessage()).contains("Twilio HTTP 500");

        // Verify retried 3 times (AC-3)
        verify(smsProvider, times(3)).dispatch(any(), any(), any());

        // Verify forwarded to DLQ
        ArgumentCaptor<DeadLetterNotificationPayload> dlqCaptor = ArgumentCaptor.forClass(DeadLetterNotificationPayload.class);
        verify(dlqPublisher, times(1)).publishToDlq(dlqCaptor.capture());

        DeadLetterNotificationPayload captured = dlqCaptor.getValue();
        assertThat(captured.eventId()).isEqualTo(eventId);
        assertThat(captured.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(captured.errorMessage()).contains("Twilio HTTP 500");
    }
}
