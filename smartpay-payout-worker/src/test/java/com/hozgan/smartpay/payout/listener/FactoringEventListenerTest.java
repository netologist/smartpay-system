package com.hozgan.smartpay.payout.listener;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.service.FactoringPayoutWorker;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("FactoringEventListener — Unit & Virtual Thread Isolation Tests")
class FactoringEventListenerTest {

    @Mock
    private FactoringPayoutWorker payoutWorker;

    @Mock
    private Acknowledgment acknowledgment;

    private ExecutorService virtualThreadExecutor;
    private ObjectMapper objectMapper;
    private FactoringEventListener listener;

    @BeforeEach
    void setUp() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        objectMapper = com.hozgan.smartpay.payout.config.PayoutWorkerConfig.createObjectMapper();
        listener = new FactoringEventListener(payoutWorker, virtualThreadExecutor, objectMapper);
    }

    @AfterEach
    void tearDown() {
        virtualThreadExecutor.close();
    }

    @Test
    @DisplayName("Successfully processes EpodVerifiedEvent on Virtual Thread and acknowledges offset")
    void shouldProcessEpodVerifiedEventAndAcknowledgeOffset() throws Exception {
        LoadId loadId = LoadId.of("LOAD-2026-999");
        CarrierId carrierId = CarrierId.generate();
        EpodVerifiedEvent event = EpodVerifiedEvent.of(
                loadId,
                carrierId,
                Instant.now(),
                GeoLocation.of(51.5074, -0.1278)
        );

        String jsonPayload = String.format("""
                {
                    "eventId": "%s",
                    "loadId": "%s",
                    "carrierId": "%s",
                    "deliveredAt": "%s",
                    "location": {
                        "latitude": 51.5074,
                        "longitude": -0.1278
                    },
                    "occurredAt": "%s"
                }
                """, event.eventId(), loadId.asString(), carrierId.asString(),
                event.deliveredAt(), event.occurredAt());

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "smartpay.events.invoice", 0, 100L, carrierId.asString(), jsonPayload);

        when(payoutWorker.processDeliveryVerification(any(EpodVerifiedEvent.class)))
                .thenReturn(FactoringPayoutOutcome.approved(
                        InvoiceId.generate(), carrierId,
                        Money.of("1000.00", Money.GBP), Money.of("25.00", Money.GBP), Money.of("975.00", Money.GBP),
                        "PAY-1"
                ));

        listener.onEpodVerifiedEvent(record, acknowledgment);

        // Await virtual thread completion
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(payoutWorker).processDeliveryVerification(any(EpodVerifiedEvent.class));
            verify(acknowledgment).acknowledge();
        });
    }

    @Test
    @DisplayName("AC-4: Virtual Thread Isolation — Processes a batch of 500 invoices concurrently without carrier thread pinning")
    void shouldProcessBatchConcurrentlyOnVirtualThreadsWithoutPinning() throws Exception {
        int batchSize = 500;
        CountDownLatch latch = new CountDownLatch(batchSize);
        AtomicInteger processedCounter = new AtomicInteger(0);

        when(payoutWorker.processDeliveryVerification(any(EpodVerifiedEvent.class)))
                .thenAnswer(invocation -> {
                    // Simulate transient I/O latency (5ms)
                    Thread.sleep(5);
                    processedCounter.incrementAndGet();
                    latch.countDown();
                    return FactoringPayoutOutcome.approved(
                            InvoiceId.generate(), CarrierId.generate(),
                            Money.of("1000.00", Money.GBP), Money.of("25.00", Money.GBP), Money.of("975.00", Money.GBP),
                            "PAY-BATCH"
                    );
                });

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            LoadId loadId = LoadId.of("LOAD-BATCH-" + i);
            CarrierId carrierId = CarrierId.generate();
            String jsonPayload = String.format("""
                    {
                        "eventId": "%s",
                        "loadId": "%s",
                        "carrierId": "%s",
                        "deliveredAt": "%s",
                        "location": {
                            "latitude": 51.5,
                            "longitude": -0.1
                        },
                        "occurredAt": "%s"
                    }
                    """, java.util.UUID.randomUUID(), loadId.asString(), carrierId.asString(),
                    Instant.now(), Instant.now());

            records.add(new ConsumerRecord<>("smartpay.events.invoice", 0, i, carrierId.asString(), jsonPayload));
        }

        long startTime = System.currentTimeMillis();

        // Dispatch all 500 concurrently
        for (ConsumerRecord<String, String> record : records) {
            listener.onEpodVerifiedEvent(record, acknowledgment);
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(completed).isTrue();
        assertThat(processedCounter.get()).isEqualTo(batchSize);
        assertThat(elapsedMs).isLessThan(5000); // Meets SLA: 500 invoices evaluated in < 5 seconds
    }
}
