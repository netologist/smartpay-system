package com.hozgan.smartpay.ledger.repository;

import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    List<JournalEntryEntity> findByTransactionId(UUID transactionId);

    List<JournalEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
