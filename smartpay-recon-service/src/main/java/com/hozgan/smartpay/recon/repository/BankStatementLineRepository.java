package com.hozgan.smartpay.recon.repository;

import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;
import com.hozgan.smartpay.recon.entity.BankStatementLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankStatementLineRepository extends JpaRepository<BankStatementLineEntity, UUID> {

    List<BankStatementLineEntity> findByStatementId(UUID statementId);

    Optional<BankStatementLineEntity> findByEndToEndId(String endToEndId);

    List<BankStatementLineEntity> findByReconciliationStatus(ReconciliationStatus status);

    List<BankStatementLineEntity> findByStatementIdAndReconciliationStatus(UUID statementId, ReconciliationStatus status);
}
