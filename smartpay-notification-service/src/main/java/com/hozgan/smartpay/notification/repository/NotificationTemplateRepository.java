package com.hozgan.smartpay.notification.repository;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.notification.entity.NotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, UUID> {

    Optional<NotificationTemplateEntity> findByTemplateCodeAndChannelAndActiveTrue(String templateCode, NotificationChannel channel);

    Optional<NotificationTemplateEntity> findByTemplateCodeAndChannel(String templateCode, NotificationChannel channel);
}
