package com.hozgan.smartpay.invoice.repository;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {

    Optional<InvoiceEntity> findByLoadId(String loadId);

    List<InvoiceEntity> findByCarrierIdAndStatus(UUID carrierId, InvoiceStatus status);

    List<InvoiceEntity> findByShipperId(UUID shipperId);
}
