package com.hozgan.smartpay.gateway.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent, lock-free token bucket rate limiter (no {@code synchronized} — Virtual Thread safe).
 *
 * <p>Each key (resolved client IP) owns a bucket that refills at {@code refillPerMinute} tokens
 * per minute up to {@code capacity}; {@code tryAcquire} grants a token only when the bucket holds
 * one. All state transitions happen inside {@link ConcurrentMap#compute}, making the check-act
 * atomic under concurrency.
 */
public class TokenBucketRateLimiter {

    private final double capacity;
    private final double refillPerNano;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, int refillPerMinute) {
        if (capacity <= 0 || refillPerMinute <= 0) {
            throw new IllegalArgumentException("capacity and refillPerMinute must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerMinute / 60_000_000_000.0;
    }

    /**
     * @return {@code true} when a token was granted; {@code false} when the bucket is exhausted.
     */
    public boolean tryAcquire(String key) {
        final boolean[] granted = {false};
        buckets.compute(key, (ignored, current) -> {
            long now = System.nanoTime();
            long elapsed = current == null ? 0 : now - current.lastRefillNanos();
            double tokens = current == null
                    ? capacity
                    : Math.min(capacity, current.tokens() + elapsed * refillPerNano);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                granted[0] = true;
            }
            return new Bucket(tokens, now);
        });
        return granted[0];
    }

    private record Bucket(double tokens, long lastRefillNanos) {
    }
}
