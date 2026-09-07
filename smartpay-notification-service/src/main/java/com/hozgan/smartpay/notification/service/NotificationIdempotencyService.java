package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(NotificationIdempotencyService.class);

    private final NotificationLogRepository logRepository;

    public NotificationIdempotencyService(NotificationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional(readOnly = true)
    public boolean isEventProcessed(UUID eventId, NotificationChannel channel) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");

        Optional<NotificationLogEntity> existing = logRepository.findByEventIdAndChannel(eventId, channel);
        if (existing.isPresent()) {
            NotificationStatus status = existing.get().getStatus();
            if (status == NotificationStatus.DISPATCHED) {
                log.info("Consumer idempotency hit: eventId={}, channel={}, status={}", eventId, channel, status);
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationLogEntity> findExistingLog(UUID eventId, NotificationChannel channel) {
        return logRepository.findByEventIdAndChannel(eventId, channel);
    }
}
