package com.hozgan.smartpay.ledger.repository;

import com.hozgan.smartpay.ledger.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByAccountNumber(String accountNumber);

    List<AccountEntity> findByEntityId(UUID entityId);
}
