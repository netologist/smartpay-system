package com.hozgan.smartpay.gateway.repository;

import com.hozgan.smartpay.gateway.entity.IdempotencyRecordEntity;
import com.hozgan.smartpay.gateway.entity.IdempotencyRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    List<IdempotencyRecordEntity> findByExpiresAtBefore(Instant threshold);
}
