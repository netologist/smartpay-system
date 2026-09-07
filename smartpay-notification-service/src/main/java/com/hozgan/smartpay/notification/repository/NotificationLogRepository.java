package com.hozgan.smartpay.notification.repository;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {

    Optional<NotificationLogEntity> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    boolean existsByEventIdAndChannelAndStatus(UUID eventId, NotificationChannel channel, NotificationStatus status);

    List<NotificationLogEntity> findByEventId(UUID eventId);

    List<NotificationLogEntity> findByStatus(NotificationStatus status);
}
