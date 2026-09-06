package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.payment.entity.TransactionalOutboxEntity;
import com.hozgan.smartpay.payment.repository.TransactionalOutboxRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Polling worker that drains the transactional outbox using {@code FOR UPDATE SKIP LOCKED}
 * semantics. Each batch is dispatched to a virtual-thread pool for concurrent processing.
 *
 * <p>In STORY-003 scope, "processing" means marking the row {@code processed_at = now()}.
 * Actual event dispatch (Kafka, bank API) is introduced in STORY-005.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisherWorker {

    private final TransactionalOutboxRepository outboxRepository;
    private final EntityManager entityManager;

    @Value("${smartpay.payment.outbox.poll-batch-size:25}")
    private int batchSize;

    @Value("${smartpay.payment.outbox.scheduled-enabled:true}")
    private boolean scheduledEnabled;

    @Scheduled(fixedDelayString = "${smartpay.payment.outbox.poll-interval-ms:500}")
    public void pollAndProcess() {
        if (!scheduledEnabled) {
            return;
        }
        List<TransactionalOutboxEntity> batch = fetchUnprocessedBatch();
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox worker picked up {} event(s) to process", batch.size());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TransactionalOutboxEntity event : batch) {
                pool.submit(() -> processEvent(event));
            }
        }
    }

    /**
     * Fetches unprocessed outbox rows using {@code FOR UPDATE SKIP LOCKED} so that multiple
     * pod instances never compete for the same row. The rows are locked for the duration of
     * this transaction; callers must finish within the poll interval.
     */
    @Transactional
    public List<TransactionalOutboxEntity> fetchUnprocessedBatch() {
        @SuppressWarnings("unchecked")
        List<TransactionalOutboxEntity> rows = entityManager.createNativeQuery(
                        "SELECT * FROM transactional_outbox " +
                        "WHERE processed_at IS NULL " +
                        "ORDER BY created_at ASC " +
                        "LIMIT :limit " +
                        "FOR UPDATE SKIP LOCKED",
                        TransactionalOutboxEntity.class)
                .setParameter("limit", batchSize)
                .getResultList();
        return rows;
    }

    /**
     * Marks a single outbox event as processed within its own transaction.
     * Any exception is caught and logged so one bad row cannot stall the entire batch.
     */
    @Transactional
    public void processEvent(TransactionalOutboxEntity event) {
        try {
            log.info("Processing outbox event: id={} type={} aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
            event.markProcessed();
            outboxRepository.save(event);
            log.info("Outbox event processed: id={}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process outbox event id={}", event.getId(), e);
        }
    }
}
