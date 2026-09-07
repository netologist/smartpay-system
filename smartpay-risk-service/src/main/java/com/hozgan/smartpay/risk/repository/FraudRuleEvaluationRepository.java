package com.hozgan.smartpay.risk.repository;

import com.hozgan.smartpay.risk.entity.FraudRuleEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FraudRuleEvaluationRepository extends JpaRepository<FraudRuleEvaluationEntity, UUID> {

    int countByCarrierIdAndEvaluatedAtAfter(UUID carrierId, Instant after);

    List<FraudRuleEvaluationEntity> findByCarrierIdOrderByEvaluatedAtDesc(UUID carrierId);
}
