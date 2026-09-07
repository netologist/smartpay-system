package com.hozgan.smartpay.notification.listener;

import com.hozgan.smartpay.common.event.FactoringPayoutApprovedEvent;
import com.hozgan.smartpay.common.event.InvoiceIssuedEvent;
import com.hozgan.smartpay.common.event.PaymentSettledEvent;
import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.common.model.id.*;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.RecipientProfile;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.service.NotificationDispatchService;
import com.hozgan.smartpay.notification.service.NotificationIdempotencyService;
import com.hozgan.smartpay.notification.service.RecipientResolver;
import com.hozgan.smartpay.notification.service.TemplateEngine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener — Acceptance Criteria & Event Consumption Tests")
class NotificationEventListenerUnitTest {

    @Mock
    private NotificationDispatchService dispatchService;

    @Mock
    private NotificationIdempotencyService idempotencyService;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private RecipientResolver recipientResolver;

    @Mock
    private Acknowledgment acknowledgment;

    private ExecutorService virtualThreadExecutor;
    private ObjectMapper objectMapper;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        objectMapper = com.hozgan.smartpay.notification.config.KafkaConfig.createObjectMapper();
        listener = new NotificationEventListener(
                dispatchService,
                idempotencyService,
                templateEngine,
                recipientResolver,
                virtualThreadExecutor,
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        virtualThreadExecutor.close();
    }

    @Test
    @DisplayName("AC-1: Instant SMS on Payment Settlement (£975.00)")
    void shouldDispatchInstantSmsOnPaymentSettlement() {
        PaymentId paymentId = PaymentId.generate();
        CarrierId carrierId = CarrierId.generate();
        Money netPayout = Money.ofGBP("975.00");
        PaymentSettledEvent event = PaymentSettledEvent.of(
                paymentId, carrierId, netPayout, "FP-9912"
        );

        when(idempotencyService.isEventProcessed(event.eventId(), NotificationChannel.SMS)).thenReturn(false);
        when(recipientResolver.resolveCarrier(carrierId))
                .thenReturn(new RecipientProfile("FastFreight Ltd", "+447700900123", "ops@fastfreight.co.uk", "http://wh", "sec"));
        when(templateEngine.render(eq("PAYMENT_SETTLED"), eq(NotificationChannel.SMS), any()))
                .thenReturn(RenderedMessage.of("SmartPay: Payout of £975.00 settled to FastFreight Ltd. Bank Ref: FP-9912."));

        NotificationDispatchResult expectedResult = new NotificationDispatchResult(
                UUID.randomUUID(), event.eventId(), NotificationChannel.SMS, "+447700900123",
                NotificationStatus.DISPATCHED, "SM-9912", null
        );
        when(dispatchService.dispatch(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        NotificationDispatchResult result = listener.processPaymentSettled(event);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.status()).isEqualTo(NotificationStatus.DISPATCHED);
        assertThat(result.providerMessageId()).isEqualTo("SM-9912");

        verify(dispatchService, times(1)).dispatch(
                eq(event.eventId()),
                eq("PAYMENT_SETTLED"),
                eq(NotificationChannel.SMS),
                eq("+447700900123"),
                eq("PAYMENT_SETTLED"),
                any(RenderedMessage.class),
                any()
        );
    }

    @Test
    @DisplayName("AC-2: Strict Consumer Idempotency suppresses duplicate SMS on replayed event")
    void shouldSuppressDuplicateNotificationOnReplay() {
        PaymentId paymentId = PaymentId.generate();
        CarrierId carrierId = CarrierId.generate();
        PaymentSettledEvent event = PaymentSettledEvent.of(
                paymentId, carrierId, Money.ofGBP("975.00"), "FP-9912"
        );

        // Already processed
        when(idempotencyService.isEventProcessed(event.eventId(), NotificationChannel.SMS)).thenReturn(true);

        NotificationDispatchResult result = listener.processPaymentSettled(event);

        assertThat(result.errorMessage()).isEqualTo("DUPLICATE_SUPPRESSED");
        verify(dispatchService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Dispatches Email on InvoiceIssuedEvent")
    void shouldDispatchEmailOnInvoiceIssued() {
        InvoiceId invoiceId = InvoiceId.generate();
        LoadId loadId = LoadId.of("LOAD-UK-555");
        ShipperId shipperId = ShipperId.generate();
        CarrierId carrierId = CarrierId.generate();
        InvoicePricing pricing = InvoicePricing.calculate(
                Money.ofGBP("1000.00"), Money.ofGBP("100.00"), Money.ofGBP("220.00")
        );

        InvoiceIssuedEvent event = InvoiceIssuedEvent.of(invoiceId, loadId, shipperId, carrierId, pricing);

        when(idempotencyService.isEventProcessed(event.eventId(), NotificationChannel.EMAIL)).thenReturn(false);
        when(recipientResolver.resolveShipper(shipperId))
                .thenReturn(new RecipientProfile("Apex Retail", "+447700900456", "billing@apex.com", "http://wh", "sec"));
        when(templateEngine.render(eq("INVOICE_ISSUED"), eq(NotificationChannel.EMAIL), any()))
                .thenReturn(RenderedMessage.of("SmartPay: Freight Invoice Issued", "Invoice total is £1320.00"));

        NotificationDispatchResult expectedResult = new NotificationDispatchResult(
                UUID.randomUUID(), event.eventId(), NotificationChannel.EMAIL, "billing@apex.com",
                NotificationStatus.DISPATCHED, "SG-1234", null
        );
        when(dispatchService.dispatch(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        NotificationDispatchResult result = listener.processInvoiceIssued(event);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.channel()).isEqualTo(NotificationChannel.EMAIL);
        verify(dispatchService, times(1)).dispatch(
                eq(event.eventId()),
                eq("INVOICE_ISSUED"),
                eq(NotificationChannel.EMAIL),
                eq("billing@apex.com"),
                eq("INVOICE_ISSUED"),
                any(RenderedMessage.class),
                any()
        );
    }

    @Test
    @DisplayName("Dispatches SMS on FactoringPayoutApprovedEvent")
    void shouldDispatchSmsOnFactoringPayoutApproved() {
        InvoiceId invoiceId = InvoiceId.generate();
        CarrierId carrierId = CarrierId.generate();
        FactoringPayoutApprovedEvent event = FactoringPayoutApprovedEvent.of(
                invoiceId, carrierId, Money.ofGBP("975.00"), Money.ofGBP("25.00")
        );

        when(idempotencyService.isEventProcessed(event.eventId(), NotificationChannel.SMS)).thenReturn(false);
        when(recipientResolver.resolveCarrier(carrierId))
                .thenReturn(new RecipientProfile("Carrier Ops", "+447700900123", "ops@carrier.com", "http://wh", "sec"));
        when(templateEngine.render(eq("FACTORING_PAYOUT_APPROVED"), eq(NotificationChannel.SMS), any()))
                .thenReturn(RenderedMessage.of("Factoring approved: £975.00"));

        NotificationDispatchResult expected = new NotificationDispatchResult(
                UUID.randomUUID(), event.eventId(), NotificationChannel.SMS, "+447700900123",
                NotificationStatus.DISPATCHED, "SM-5555", null
        );
        when(dispatchService.dispatch(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);

        NotificationDispatchResult result = listener.processFactoringPayoutApproved(event);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("Processes PaymentSettledEvent asynchronously on Virtual Thread and acknowledges offset")
    void shouldProcessOnVirtualThreadAndAcknowledgeOffset() throws Exception {
        PaymentId paymentId = PaymentId.generate();
        CarrierId carrierId = CarrierId.generate();
        PaymentSettledEvent event = PaymentSettledEvent.of(
                paymentId, carrierId, Money.ofGBP("975.00"), "FP-9912"
        );

        String json = """
                {
                    "eventId": "%s",
                    "paymentId": "%s",
                    "carrierId": "%s",
                    "settledAmount": {
                        "amount": 975.00,
                        "currency": "GBP"
                    },
                    "bankReference": "FP-9912",
                    "occurredAt": "%s"
                }
                """.formatted(event.eventId(), paymentId, carrierId.asString(), event.occurredAt());

        when(idempotencyService.isEventProcessed(event.eventId(), NotificationChannel.SMS)).thenReturn(false);
        when(recipientResolver.resolveCarrier(carrierId))
                .thenReturn(new RecipientProfile("FastFreight Ltd", "+447700900123", "ops@fastfreight.co.uk", "http://wh", "sec"));
        when(templateEngine.render(any(), any(), any()))
                .thenReturn(RenderedMessage.of("SmartPay: Payout £975.00 settled"));

        NotificationDispatchResult expectedResult = new NotificationDispatchResult(
                UUID.randomUUID(), event.eventId(), NotificationChannel.SMS, "+447700900123",
                NotificationStatus.DISPATCHED, "SM-9912", null
        );
        when(dispatchService.dispatch(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "smartpay.events.payment", 0, 42L, paymentId.toString(), json
        );

        listener.onPaymentSettled(record, acknowledgment);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(acknowledgment, atLeastOnce()).acknowledge()
        );
    }
}
