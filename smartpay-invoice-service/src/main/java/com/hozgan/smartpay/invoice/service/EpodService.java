package com.hozgan.smartpay.invoice.service;

import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.exception.DuplicateLoadException;
import com.hozgan.smartpay.common.exception.InvalidEpodSignatureException;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.SignatureHash;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import com.hozgan.smartpay.invoice.repository.EpodRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EpodService {

    private static final Logger log = LoggerFactory.getLogger(EpodService.class);

    private final EpodRecordRepository epodRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EpodService(EpodRecordRepository epodRecordRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.epodRecordRepository = epodRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EpodRecordEntity verifyAndRecordEpod(String loadId,
                                               UUID carrierId,
                                               Instant deliveredAt,
                                               BigDecimal latitude,
                                               BigDecimal longitude,
                                               String photoS3Url,
                                               String signatureHash) {
        Objects.requireNonNull(loadId, "loadId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(deliveredAt, "deliveredAt cannot be null");
        Objects.requireNonNull(photoS3Url, "photoS3Url cannot be null");

        // 1. Cryptographic signature hash validation
        try {
            SignatureHash.of(signatureHash);
        } catch (Exception e) {
            log.warn("Invalid ePOD signature hash for load {}: {}", loadId, signatureHash);
            throw new InvalidEpodSignatureException(new LoadId(loadId));
        }

        // 2. Geospatial boundary validation (-90 to 90 lat, -180 to 180 lon)
        GeoLocation location = new GeoLocation(latitude, longitude);

        // 3. Prevent duplicate delivery proofs for the same load (AC-4)
        if (epodRecordRepository.findByLoadId(loadId).isPresent()) {
            log.warn("ePOD record already exists for load: {}", loadId);
            throw new DuplicateLoadException(new LoadId(loadId));
        }

        // 4. Persist verified ePOD record
        EpodRecordEntity epod = new EpodRecordEntity(
                loadId,
                carrierId,
                deliveredAt,
                location,
                photoS3Url,
                signatureHash,
                true
        );
        EpodRecordEntity saved = epodRecordRepository.save(epod);
        log.info("ePOD successfully verified and saved for load: {}, id: {}", loadId, saved.getId());

        // 5. Emit EpodVerifiedEvent
        EpodVerifiedEvent event = EpodVerifiedEvent.of(
                new LoadId(loadId),
                new CarrierId(carrierId),
                deliveredAt,
                location
        );
        eventPublisher.publishEvent(event);

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<EpodRecordEntity> findByLoadId(String loadId) {
        return epodRecordRepository.findByLoadId(loadId);
    }
}
