package com.hozgan.smartpay.notification.listener;

import com.hozgan.smartpay.common.event.FactoringPayoutApprovedEvent;
import com.hozgan.smartpay.common.event.InvoiceIssuedEvent;
import com.hozgan.smartpay.common.event.PaymentSettledEvent;
import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.RecipientProfile;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.service.NotificationDispatchService;
import com.hozgan.smartpay.notification.service.NotificationIdempotencyService;
import com.hozgan.smartpay.notification.service.RecipientResolver;
import com.hozgan.smartpay.notification.service.TemplateEngine;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Event-driven Kafka listener in consumer group smartpay-notification-workers (STORY-008).
 * Consumes PaymentSettledEvent, InvoiceIssuedEvent, and FactoringPayoutApprovedEvent.
 * Dispatches asynchronously using Java 25 Virtual Threads.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationDispatchService dispatchService;
    private final NotificationIdempotencyService idempotencyService;
    private final TemplateEngine templateEngine;
    private final RecipientResolver recipientResolver;
    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(
            NotificationDispatchService dispatchService,
            NotificationIdempotencyService idempotencyService,
            TemplateEngine templateEngine,
            RecipientResolver recipientResolver,
            ExecutorService virtualThreadExecutor,
            ObjectMapper objectMapper) {
        this.dispatchService = dispatchService;
        this.idempotencyService = idempotencyService;
        this.templateEngine = templateEngine;
        this.recipientResolver = recipientResolver;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${smartpay.notification.topics.payment-events:smartpay.events.payment}",
            groupId = "${smartpay.notification.consumer-group-id:smartpay-notification-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSettled(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record cannot be null");

        log.info("Received PaymentSettled event: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        CompletableFuture.runAsync(() -> {
            try {
                PaymentSettledEvent event = objectMapper.readValue(record.value(), PaymentSettledEvent.class);
                processPaymentSettled(event);

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                    log.debug("Offset acknowledged for PaymentSettled: offset={}", record.offset());
                }
            } catch (Exception e) {
                log.error("Failed to process PaymentSettled event at offset {}: {}", record.offset(), e.getMessage(), e);
                throw new IllegalStateException("PaymentSettled notification processing failed", e);
            }
        }, virtualThreadExecutor);
    }

    @KafkaListener(
            topics = "${smartpay.notification.topics.invoice-events:smartpay.events.invoice}",
            groupId = "${smartpay.notification.consumer-group-id:smartpay-notification-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInvoiceIssued(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record cannot be null");

        log.info("Received InvoiceIssued event: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        CompletableFuture.runAsync(() -> {
            try {
                InvoiceIssuedEvent event = objectMapper.readValue(record.value(), InvoiceIssuedEvent.class);
                processInvoiceIssued(event);

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                    log.debug("Offset acknowledged for InvoiceIssued: offset={}", record.offset());
                }
            } catch (Exception e) {
                log.error("Failed to process InvoiceIssued event at offset {}: {}", record.offset(), e.getMessage(), e);
                throw new IllegalStateException("InvoiceIssued notification processing failed", e);
            }
        }, virtualThreadExecutor);
    }

    @KafkaListener(
            topics = "${smartpay.notification.topics.payout-events:smartpay.events.payout}",
            groupId = "${smartpay.notification.consumer-group-id:smartpay-notification-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFactoringPayoutApproved(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record cannot be null");

        log.info("Received FactoringPayoutApproved event: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        CompletableFuture.runAsync(() -> {
            try {
                FactoringPayoutApprovedEvent event = objectMapper.readValue(record.value(), FactoringPayoutApprovedEvent.class);
                processFactoringPayoutApproved(event);

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                    log.debug("Offset acknowledged for FactoringPayoutApproved: offset={}", record.offset());
                }
            } catch (Exception e) {
                log.error("Failed to process FactoringPayoutApproved event at offset {}: {}", record.offset(), e.getMessage(), e);
                throw new IllegalStateException("FactoringPayoutApproved notification processing failed", e);
            }
        }, virtualThreadExecutor);
    }

    /**
     * Synchronous / direct processing of PaymentSettledEvent (used by listener and unit tests).
     * Dispatches instant SMS to carrier (AC-1) with consumer idempotency (AC-2).
     */
    public NotificationDispatchResult processPaymentSettled(PaymentSettledEvent event) {
        NotificationChannel channel = NotificationChannel.SMS;

        // Idempotency check (AC-2)
        if (idempotencyService.isEventProcessed(event.eventId(), channel)) {
            log.info("Suppressing duplicate SMS notification for eventId={}", event.eventId());
            return new NotificationDispatchResult(
                    null, event.eventId(), channel, null,
                    com.hozgan.smartpay.common.model.enums.NotificationStatus.DISPATCHED, null, "DUPLICATE_SUPPRESSED"
            );
        }

        RecipientProfile carrier = recipientResolver.resolveCarrier(event.carrierId());
        String carrierName = event.carrierName() != null ? event.carrierName() : carrier.name();

        Map<String, String> params = new HashMap<>();
        params.put("carrierName", carrierName);
        params.put("formattedAmount", event.settledAmount().formatted());
        params.put("bankRef", event.bankReference());
        params.put("paymentId", event.paymentId().toString());
        params.put("carrierId", event.carrierId().asString());
        params.put("timestamp", event.occurredAt().toString());

        RenderedMessage message = templateEngine.render("PAYMENT_SETTLED", channel, params);

        Map<String, String> metadata = Map.of(
                "webhookSecret", carrier.webhookSecret(),
                "carrierId", event.carrierId().asString()
        );

        return dispatchService.dispatch(
                event.eventId(),
                "PAYMENT_SETTLED",
                channel,
                carrier.phone(),
                "PAYMENT_SETTLED",
                message,
                metadata
        );
    }

    /**
     * Synchronous / direct processing of InvoiceIssuedEvent.
     * Dispatches Email notification to Shipper.
     */
    public NotificationDispatchResult processInvoiceIssued(InvoiceIssuedEvent event) {
        NotificationChannel channel = NotificationChannel.EMAIL;

        if (idempotencyService.isEventProcessed(event.eventId(), channel)) {
            log.info("Suppressing duplicate InvoiceIssued email for eventId={}", event.eventId());
            return new NotificationDispatchResult(
                    null, event.eventId(), channel, null,
                    com.hozgan.smartpay.common.model.enums.NotificationStatus.DISPATCHED, null, "DUPLICATE_SUPPRESSED"
            );
        }

        RecipientProfile shipper = recipientResolver.resolveShipper(event.shipperId());

        Map<String, String> params = new HashMap<>();
        params.put("invoiceId", event.invoiceId().asString());
        params.put("loadId", event.loadId().asString());
        params.put("shipperId", event.shipperId().asString());
        params.put("formattedAmount", event.pricing().totalAmount().formatted());
        params.put("timestamp", event.occurredAt().toString());

        RenderedMessage message = templateEngine.render("INVOICE_ISSUED", channel, params);

        return dispatchService.dispatch(
                event.eventId(),
                "INVOICE_ISSUED",
                channel,
                shipper.email(),
                "INVOICE_ISSUED",
                message,
                Map.of("shipperId", event.shipperId().asString())
        );
    }

    /**
     * Synchronous / direct processing of FactoringPayoutApprovedEvent.
     * Dispatches SMS to Carrier.
     */
    public NotificationDispatchResult processFactoringPayoutApproved(FactoringPayoutApprovedEvent event) {
        NotificationChannel channel = NotificationChannel.SMS;

        if (idempotencyService.isEventProcessed(event.eventId(), channel)) {
            log.info("Suppressing duplicate FactoringPayoutApproved SMS for eventId={}", event.eventId());
            return new NotificationDispatchResult(
                    null, event.eventId(), channel, null,
                    com.hozgan.smartpay.common.model.enums.NotificationStatus.DISPATCHED, null, "DUPLICATE_SUPPRESSED"
            );
        }

        RecipientProfile carrier = recipientResolver.resolveCarrier(event.carrierId());

        Map<String, String> params = new HashMap<>();
        params.put("invoiceId", event.invoiceId().asString());
        params.put("carrierId", event.carrierId().asString());
        params.put("formattedAmount", event.payoutAmount().formatted());
        params.put("timestamp", event.occurredAt().toString());

        RenderedMessage message = templateEngine.render("FACTORING_PAYOUT_APPROVED", channel, params);

        return dispatchService.dispatch(
                event.eventId(),
                "FACTORING_PAYOUT_APPROVED",
                channel,
                carrier.phone(),
                "FACTORING_PAYOUT_APPROVED",
                message,
                Map.of("carrierId", event.carrierId().asString())
        );
    }
}
