package com.hozgan.smartpay.gateway.repository;

import com.hozgan.smartpay.common.model.enums.IdempotencyStatus;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    List<IdempotencyRecordEntity> findByExpiresAtBefore(Instant threshold);

    /**
     * Atomically reclaims an idempotency slot whose previous attempt is no longer authoritative:
     * a {@code FAILED} terminal attempt, or any row whose TTL has elapsed (orphaned PROCESSING
     * lock or expired COMPLETED replay). Returns the number of rows reset — {@code 1} when this
     * caller won the race, {@code 0} when a concurrent caller already reclaimed the slot.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IdempotencyRecordEntity e
               SET e.status = :processing,
                   e.requestHash = :requestHash,
                   e.expiresAt = :expiresAt,
                   e.responseCode = NULL,
                   e.responseBody = NULL
             WHERE e.id = :id
               AND (e.status = :failed OR e.expiresAt < :now)
            """)
    int resetForRetry(@Param("id") IdempotencyRecordId id,
                      @Param("requestHash") String requestHash,
                      @Param("processing") IdempotencyStatus processing,
                      @Param("failed") IdempotencyStatus failed,
                      @Param("expiresAt") Instant expiresAt,
                      @Param("now") Instant now);
}
