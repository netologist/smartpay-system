package com.hozgan.smartpay.invoice.repository;

import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EpodRecordRepository extends JpaRepository<EpodRecordEntity, UUID> {

    Optional<EpodRecordEntity> findByLoadId(LoadId loadId);

    List<EpodRecordEntity> findByCarrierIdOrderByDeliveredAtDesc(CarrierId carrierId);
}
