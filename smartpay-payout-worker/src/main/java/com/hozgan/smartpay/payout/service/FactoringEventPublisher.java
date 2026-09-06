package com.hozgan.smartpay.payout.service;

import com.hozgan.smartpay.common.event.FactoringPayoutApprovedEvent;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.payout.config.PayoutWorkerProperties;
import com.hozgan.smartpay.payout.domain.FactoringCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class FactoringEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FactoringEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PayoutWorkerProperties properties;

    public FactoringEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                   PayoutWorkerProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Publishes FactoringPayoutApprovedEvent to Kafka topic smartpay.events.factoring.
     */
    public FactoringPayoutApprovedEvent publishApprovedPayout(
            InvoiceId invoiceId,
            CarrierId carrierId,
            FactoringCalculationResult calculation) {
        Objects.requireNonNull(invoiceId, "invoiceId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(calculation, "calculation cannot be null");

        FactoringPayoutApprovedEvent event = FactoringPayoutApprovedEvent.of(
                invoiceId,
                carrierId,
                calculation.grossAmount(),
                calculation.netPayoutAmount(),
                calculation.factoringFee()
        );

        String topic = properties.topics().factoringEvents();
        String partitionKey = carrierId.asString();

        log.info("Publishing FactoringPayoutApprovedEvent to topic {} with key {}: invoiceId={}, netPayout={}, fee={}",
                topic, partitionKey, invoiceId, calculation.netPayoutAmount(), calculation.factoringFee());

        try {
            kafkaTemplate.send(topic, partitionKey, event)
                    .get(5, TimeUnit.SECONDS);
            log.info("Successfully published FactoringPayoutApprovedEvent for invoiceId={}", invoiceId);
        } catch (Exception e) {
            log.error("Failed to publish FactoringPayoutApprovedEvent for invoiceId={}: {}", invoiceId, e.getMessage(), e);
            throw new IllegalStateException("Failed to publish factoring event to Kafka", e);
        }

        return event;
    }
}
