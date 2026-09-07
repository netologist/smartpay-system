package com.hozgan.smartpay.gateway.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TokenBucketRateLimiterTest {

    @Test
    void tryAcquire_allowsUpToCapacityThenRejects() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 60);

        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isFalse();
    }

    @Test
    void tryAcquire_bucketsAreIsolatedPerKey() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 60);

        assertThat(limiter.tryAcquire("203.0.113.1")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.1")).isFalse();
        // A different client retains its own full bucket.
        assertThat(limiter.tryAcquire("203.0.113.2")).isTrue();
    }

    @Test
    void tryAcquire_refillsTokensOverTime() throws InterruptedException {
        // 600 tokens/minute == 10 tokens/second: a 150ms wait restores > 1 token deterministically.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 600);

        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.7")).isFalse();

        Thread.sleep(250);

        assertThat(limiter.tryAcquire("203.0.113.7")).isTrue();
    }
}
