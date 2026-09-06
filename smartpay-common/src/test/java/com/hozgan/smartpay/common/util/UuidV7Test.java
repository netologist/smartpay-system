package com.hozgan.smartpay.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;

@Tag("unit")
@DisplayName("UUIDv7 Generator Tests (RFC 9562)")
class UuidV7Test {

    @Test
    @DisplayName("Generated UUID has version 7 and variant 2")
    void versionAndVariantAreCorrect() {
        UUID uuid = UuidV7.generate();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(UuidV7.isUuidV7(uuid)).isTrue();
        assertThat(UuidV7.isUuidV7(UUID.randomUUID())).isFalse(); // v4 has version 4
    }

    @Test
    @DisplayName("Timestamp extracted matches generation time closely")
    void timestampExtractionIsAccurate() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.generate();
        long after = System.currentTimeMillis();

        Instant extracted = UuidV7.extractTimestamp(uuid);
        assertThat(extracted.toEpochMilli()).isBetween(before, after);
    }

    @Test
    @DisplayName("Anchored generation uses provided Instant timestamp")
    void anchoredGenerationUsesProvidedInstant() {
        Instant specificTime = Instant.parse("2026-09-05T12:00:00Z");
        UUID uuid = UuidV7.generate(specificTime);

        assertThat(UuidV7.extractTimestamp(uuid)).isEqualTo(specificTime);
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    @DisplayName("Monotonicity: sequentially generated UUIDs are ordered chronologically")
    void sequentiallyGeneratedAreOrdered() throws InterruptedException {
        UUID first = UuidV7.generate();
        Thread.sleep(2);
        UUID second = UuidV7.generate();

        assertThat(first.compareTo(second)).isNegative();
        assertThat(UuidV7.extractTimestamp(first)).isBeforeOrEqualTo(UuidV7.extractTimestamp(second));
    }

    @Test
    @DisplayName("Virtual threads high concurrency generates unique UUIDs without collision")
    void concurrentGenerationHasNoCollisions() throws InterruptedException {
        int threadCount = 100;
        int perThreadCount = 100;
        int totalExpected = threadCount * perThreadCount;

        Set<UUID> generated = ConcurrentHashMap.newKeySet(totalExpected);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < perThreadCount; i++) {
                            generated.add(UuidV7.generate());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(generated).hasSize(totalExpected);
        }
    }
}
