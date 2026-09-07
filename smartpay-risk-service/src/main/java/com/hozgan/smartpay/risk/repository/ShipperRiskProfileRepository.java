package com.hozgan.smartpay.risk.repository;

import com.hozgan.smartpay.risk.entity.ShipperRiskProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipperRiskProfileRepository extends JpaRepository<ShipperRiskProfileEntity, UUID> {
}
