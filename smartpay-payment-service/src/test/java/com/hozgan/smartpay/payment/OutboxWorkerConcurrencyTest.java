package com.hozgan.smartpay.payment;

import com.hozgan.smartpay.payment.entity.TransactionalOutboxEntity;
import com.hozgan.smartpay.payment.repository.TransactionalOutboxRepository;
import com.hozgan.smartpay.payment.service.OutboxEventPublisherWorker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class OutboxWorkerConcurrencyTest {

    @Autowired private TransactionalOutboxRepository outboxRepository;
    @Autowired private OutboxEventPublisherWorker worker;
    @Autowired private PlatformTransactionManager transactionManager;
    @Test
    void concurrentWorkers_receiveDisjointBatches_noContention() throws InterruptedException {
        // Insert 100 unprocessed events
        List<TransactionalOutboxEntity> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(new TransactionalOutboxEntity("PAYMENT", UUID.randomUUID().toString(),
                    "PAYMENT_INITIATED", "{\"index\":" + i + "}"));
        }
        outboxRepository.saveAll(events);

        // 4 concurrent workers poll simultaneously
        int workerCount = 4;
        CountDownLatch doneLatch = new CountDownLatch(workerCount);
        CyclicBarrier barrier = new CyclicBarrier(workerCount);
        Set<UUID> seenIds = ConcurrentHashMap.newKeySet();
        List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < workerCount; i++) {
                pool.submit(() -> {
                    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    try {
                        txTemplate.execute(status -> {
                            List<TransactionalOutboxEntity> batch = worker.fetchUnprocessedBatch();
                            batchSizes.add(batch.size());
                            batch.forEach(e -> seenIds.add(e.getId()));
                            try {
                                barrier.await(10, TimeUnit.SECONDS);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                            return null;
                        });
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        }

        // Each worker got 25 disjoint rows (SKIP LOCKED ensures no duplicates across workers)
        assertThat(seenIds).hasSize(100);
        assertThat(batchSizes).allSatisfy(size -> assertThat(size).isEqualTo(25));
    }
}
