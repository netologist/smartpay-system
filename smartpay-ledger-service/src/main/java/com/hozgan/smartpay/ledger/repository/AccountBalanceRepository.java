package com.hozgan.smartpay.ledger.repository;

import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, UUID> {

    Optional<AccountBalanceEntity> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM AccountBalanceEntity b WHERE b.accountId = :accountId")
    Optional<AccountBalanceEntity> findByAccountIdWithLock(@Param("accountId") UUID accountId);
}
