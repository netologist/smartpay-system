package com.hozgan.smartpay.ledger.repository;

import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalTransactionRepository extends JpaRepository<JournalTransactionEntity, UUID> {

    Optional<JournalTransactionEntity> findByIdempotencyKey(String idempotencyKey);

    List<JournalTransactionEntity> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
