package com.hozgan.smartpay.payment.repository;

import com.hozgan.smartpay.payment.entity.TransactionalOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionalOutboxRepository extends JpaRepository<TransactionalOutboxEntity, UUID> {

    @Query("SELECT o FROM TransactionalOutboxEntity o WHERE o.processedAt IS NULL ORDER BY o.createdAt ASC")
    List<TransactionalOutboxEntity> findUnprocessedEvents(Pageable pageable);

    List<TransactionalOutboxEntity> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);
}
