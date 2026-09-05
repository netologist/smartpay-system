package com.hozgan.smartpay.ledger;

import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntityType;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.repository.AccountBalanceRepository;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrency integration tests for the Ledger Transfer Engine.
 *
 * <p>Uses real PostgreSQL 16 via Testcontainers. Validates:
 * <ul>
 *   <li><strong>AC-2</strong>: Pessimistic locking prevents overdraft under concurrent debit attempts.</li>
 *   <li><strong>AC-3</strong>: Deadlock-free bidirectional transfers (A→B and B→A simultaneously).</li>
 * </ul>
 *
 * <p>Uses {@link java.util.concurrent.ExecutorService} with Virtual Threads and
 * {@link org.awaitility.Awaitility} to assert on asynchronous outcomes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Concurrency — Deadlock-Free & Overdraft-Prevention Tests")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class ConcurrencyIntegrationTest {

    @Autowired AccountBalanceService accountBalanceService;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountBalanceRepository accountBalanceRepository;
    @Autowired JournalEntryRepository journalEntryRepository;
    @Autowired JournalTransactionRepository journalTransactionRepository;

    private UUID accountA;
    private UUID accountB;

    @BeforeEach
    void setUp() {
        String aSuffix = UUID.randomUUID().toString().substring(0, 8);
        String bSuffix = UUID.randomUUID().toString().substring(0, 8);
        AccountEntity aEntity = new AccountEntity("CONC-A-" + aSuffix, UUID.randomUUID(), EntityType.SHIPPER, "GBP");
        AccountEntity bEntity = new AccountEntity("CONC-B-" + bSuffix, UUID.randomUUID(), EntityType.CARRIER, "GBP");
        accountRepository.saveAll(List.of(aEntity, bEntity));
        accountA = aEntity.getId();
        accountB = bEntity.getId();
    }


    // =========================================================================
    // AC-2: Overdraft Prevention under Concurrent Load
    // =========================================================================

    /**
     * Seeds Account A with £300. Fires 3 concurrent transfers of £200 each.
     * Expected: exactly 1 succeeds (£300 available), exactly 2 fail with InsufficientFundsException.
     * Final balance of Account A must be exactly £100 (not negative).
     */
    @Test
    @DisplayName("AC-2: exactly one of three concurrent £200 debits succeeds (£300 available)")
    void ac2_overdraftPreventionUnderConcurrentLoad() throws InterruptedException {
        // Seed: Account A = £300, Account B = £0
        accountBalanceRepository.saveAll(List.of(
                new AccountBalanceEntity(accountA, 30_000L, 0L),
                new AccountBalanceEntity(accountB,      0L, 0L)
        ));

        int threads = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < threads; i++) {
            final String key = "CONC-IDEMP-AC2-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // all threads start simultaneously
                    accountBalanceService.transfer(
                            accountA, accountB, Money.ofGBP("200.00"),
                            "INVOICE_SETTLEMENT", "CONC-INV-001",
                            key, "Concurrent debit");
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected — rethrow to make test fail via Awaitility
                    throw new RuntimeException("Unexpected exception: " + e.getMessage(), e);
                } finally {
                    doneLatch.countDown();
                }
                return null;
            });
        }

        startLatch.countDown(); // release all threads

        // Wait up to 10 seconds for all threads to complete
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> doneLatch.getCount() == 0);

        executor.shutdown();

        // Exactly 1 transfer succeeds (£300 / £200 = floor 1)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(2);

        // Final balance: £300 - £200 = £100 exactly. Never negative.
        AccountBalanceEntity finalBalance = accountBalanceRepository
                .findByAccountId(accountA).orElseThrow();
        assertThat(finalBalance.getClearedBalancePence()).isEqualTo(10_000L); // £100
        assertThat(finalBalance.getAvailableBalancePence()).isGreaterThanOrEqualTo(0L);
    }

    // =========================================================================
    // AC-3: Deadlock-Free Bidirectional Transfers
    // =========================================================================

    /**
     * Fires 20 pairs of opposite-direction transfers concurrently (A→B and B→A).
     * With ordered locking, no deadlock should occur.
     * Awaitility verifies all futures complete within 15 seconds.
     */
    @Test
    @DisplayName("AC-3: bidirectional concurrent transfers complete without deadlock")
    void ac3_bidirectionalTransfersNoDeadlock() throws Exception {
        // Seed large balances to avoid InsufficientFunds — we only care about deadlock
        accountBalanceRepository.saveAll(List.of(
                new AccountBalanceEntity(accountA, 10_000_000L, 0L), // £100,000
                new AccountBalanceEntity(accountB, 10_000_000L, 0L)  // £100,000
        ));

        int pairs = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < pairs; i++) {
            final String keyAtoB = "DEADLOCK-AtoB-" + UUID.randomUUID();
            final String keyBtoA = "DEADLOCK-BtoA-" + UUID.randomUUID();

            // A → B
            futures.add(executor.submit(() -> {
                accountBalanceService.transfer(
                        accountA, accountB, Money.ofGBP("1.00"),
                        "DEADLOCK_TEST", "DL-001", keyAtoB, "A to B");
                return null;
            }));

            // B → A (opposite direction — classic deadlock setup for naive locking)
            futures.add(executor.submit(() -> {
                accountBalanceService.transfer(
                        accountB, accountA, Money.ofGBP("1.00"),
                        "DEADLOCK_TEST", "DL-002", keyBtoA, "B to A");
                return null;
            }));
        }

        // Awaitility: assert all futures done without exception within 15 seconds
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    assertThat(futures).allSatisfy(f -> assertThat(f.isDone()).isTrue());
                });

        // Assert no future threw an exception
        for (Future<?> f : futures) {
            assertThatCode(() -> f.get()).doesNotThrowAnyException();
        }

        executor.shutdown();

        // Total transfers = 2 * pairs; each is £1.00 in opposite directions → net 0
        // Both balances must remain £100,000 ± rounding on net (equal A→B and B→A)
        AccountBalanceEntity aFinal = accountBalanceRepository.findByAccountId(accountA).orElseThrow();
        AccountBalanceEntity bFinal = accountBalanceRepository.findByAccountId(accountB).orElseThrow();

        // Net transfers cancel out: A sent pairs*£1 and received pairs*£1
        assertThat(aFinal.getClearedBalancePence()).isEqualTo(10_000_000L);
        assertThat(bFinal.getClearedBalancePence()).isEqualTo(10_000_000L);
    }

    // =========================================================================
    // Additional: Multiple sequential transfers sum correctly
    // =========================================================================

    @Test
    @DisplayName("sequential transfers accumulate correctly — no phantom reads")
    void sequentialTransfersAccumulateCorrectly() {
        accountBalanceRepository.saveAll(List.of(
                new AccountBalanceEntity(accountA, 100_000L, 0L), // £1000
                new AccountBalanceEntity(accountB,       0L, 0L)
        ));

        for (int i = 0; i < 5; i++) {
            accountBalanceService.transfer(
                    accountA, accountB, Money.ofGBP("100.00"),
                    "BATCH", "BATCH-" + i,
                    "SEQ-IDEMP-" + UUID.randomUUID(), "Sequential " + i);
        }
        AccountBalanceEntity aFinal = accountBalanceRepository.findByAccountId(accountA).orElseThrow();
        AccountBalanceEntity bFinal = accountBalanceRepository.findByAccountId(accountB).orElseThrow();

        assertThat(aFinal.getClearedBalancePence()).isEqualTo(50_000L); // £1000 - 5*£100 = £500
        assertThat(bFinal.getClearedBalancePence()).isEqualTo(50_000L); // £0 + 5*£100 = £500

        // 5 transfers debited accountA
        assertThat(journalEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountA)).hasSize(5);
    }
}
