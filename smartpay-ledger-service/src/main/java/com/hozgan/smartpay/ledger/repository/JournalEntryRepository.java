package com.hozgan.smartpay.ledger.repository;

import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.TransactionId;
import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, UUID> {

    List<JournalEntryEntity> findByTransactionId(TransactionId transactionId);

    default List<JournalEntryEntity> findByTransactionId(UUID transactionId) {
        return findByTransactionId(TransactionId.of(transactionId));
    }

    List<JournalEntryEntity> findByAccountIdOrderByCreatedAtDesc(AccountId accountId);

    default List<JournalEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId) {
        return findByAccountIdOrderByCreatedAtDesc(AccountId.of(accountId));
    }
}
