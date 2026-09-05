package com.hozgan.smartpay.recon.repository;

import com.hozgan.smartpay.recon.entity.BankStatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatementEntity, UUID> {

    Optional<BankStatementEntity> findByStatementReference(String statementReference);

    List<BankStatementEntity> findByStatementDateBetweenOrderByStatementDateDesc(LocalDate start, LocalDate end);
}
