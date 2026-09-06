package com.hozgan.smartpay.payout.listener;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.service.FactoringPayoutWorker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Event-driven Kafka listener in consumer group smartpay-factoring-workers (ADR-008).
 * Consumes EpodVerifiedEvent from smartpay.events.invoice.
 * Offloads execution to Java 25 Virtual Threads to prevent OS thread pinning.
 */
@Component
public class FactoringEventListener {

    private static final Logger log = LoggerFactory.getLogger(FactoringEventListener.class);

    private final FactoringPayoutWorker payoutWorker;
    private final ExecutorService virtualThreadExecutor;
    private final ObjectMapper objectMapper;

    public FactoringEventListener(FactoringPayoutWorker payoutWorker,
                                  ExecutorService virtualThreadExecutor,
                                  ObjectMapper objectMapper) {
        this.payoutWorker = payoutWorker;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${smartpay.payout.topics.invoice-events:smartpay.events.invoice}",
            groupId = "${smartpay.payout.consumer-group-id:smartpay-factoring-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEpodVerifiedEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record cannot be null");

        log.info("Received EpodVerifiedEvent: topic={}, partition={}, offset={}, key={}",
                record.topic(), record.partition(), record.offset(), record.key());

        // Dispatch immediately to a Java 25 Virtual Thread (ADR-008 / AC-4)
        CompletableFuture.runAsync(() -> {
            try {
                EpodVerifiedEvent event = parseEvent(record.value());
                log.debug("Dispatching factoring workflow for loadId={} on virtual thread: {}",
                        event.loadId(), Thread.currentThread());

                FactoringPayoutOutcome outcome = payoutWorker.processDeliveryVerification(event);
                log.info("Factoring workflow finished for loadId={}: outcome={}", event.loadId(), outcome.status());

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                    log.debug("Offset acknowledged for partition={}, offset={}", record.partition(), record.offset());
                }
            } catch (Exception e) {
                log.error("Unhandled error processing EpodVerifiedEvent at offset {}: {}",
                        record.offset(), e.getMessage(), e);
                throw new IllegalStateException("Factoring worker virtual thread execution failed", e);
            }
        }, virtualThreadExecutor);
    }

    /**
     * Process directly (used for synchronous invocations and unit tests).
     */
    public FactoringPayoutOutcome processDirect(EpodVerifiedEvent event) {
        return payoutWorker.processDeliveryVerification(event);
    }

    private EpodVerifiedEvent parseEvent(String payload) {
        try {
            return objectMapper.readValue(payload, EpodVerifiedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse EpodVerifiedEvent payload: {}", payload, e);
            throw new IllegalArgumentException("Invalid EpodVerifiedEvent JSON payload", e);
        }
    }
}
