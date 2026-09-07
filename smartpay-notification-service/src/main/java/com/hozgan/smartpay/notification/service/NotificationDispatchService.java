package com.hozgan.smartpay.notification.service;

import com.hozgan.smartpay.common.model.enums.NotificationChannel;
import com.hozgan.smartpay.common.model.enums.NotificationStatus;
import com.hozgan.smartpay.notification.entity.NotificationLogEntity;
import com.hozgan.smartpay.notification.exception.NotificationDeliveryException;
import com.hozgan.smartpay.notification.model.DeadLetterNotificationPayload;
import com.hozgan.smartpay.notification.model.NotificationDispatchResult;
import com.hozgan.smartpay.notification.model.ProviderReceipt;
import com.hozgan.smartpay.notification.model.RenderedMessage;
import com.hozgan.smartpay.notification.provider.NotificationProvider;
import com.hozgan.smartpay.notification.repository.NotificationLogRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final Map<NotificationChannel, NotificationProvider> providers = new EnumMap<>(NotificationChannel.class);
    private final NotificationLogRepository logRepository;
    private final NotificationDlqPublisher dlqPublisher;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public NotificationDispatchService(
            List<NotificationProvider> providerList,
            NotificationLogRepository logRepository,
            NotificationDlqPublisher dlqPublisher,
            @Autowired(required = false) RetryRegistry retryRegistry,
            @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry) {

        for (NotificationProvider provider : providerList) {
            providers.put(provider.channel(), provider);
        }

        this.logRepository = logRepository;
        this.dlqPublisher = dlqPublisher;

        // Configure Resilience4j Retry instance
        if (retryRegistry != null) {
            this.retry = retryRegistry.retry("notification-provider");
        } else {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(3)
                    .waitDuration(Duration.ofMillis(100))
                    .retryExceptions(NotificationDeliveryException.class)
                    .build();
            this.retry = Retry.of("notification-provider-default", config);
        }

        // Configure Resilience4j CircuitBreaker instance
        if (circuitBreakerRegistry != null) {
            this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification-provider");
        } else {
            this.circuitBreaker = CircuitBreaker.ofDefaults("notification-provider-default");
        }
    }

    @Transactional
    public NotificationDispatchResult dispatch(
            UUID eventId,
            String eventType,
            NotificationChannel channel,
            String recipient,
            String templateCode,
            RenderedMessage message,
            Map<String, String> metadata) {

        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        NotificationProvider provider = providers.get(channel);
        if (provider == null) {
            throw new IllegalArgumentException("No registered provider found for channel: " + channel);
        }

        // 1. Create or retrieve NotificationLogEntity
        NotificationLogEntity logEntity = logRepository.findByEventIdAndChannel(eventId, channel)
                .orElseGet(() -> new NotificationLogEntity(
                        eventId, eventType, channel, recipient, templateCode, message.content(), NotificationStatus.PENDING
                ));

        if (logEntity.getStatus() == NotificationStatus.DISPATCHED) {
            log.info("Notification already dispatched: eventId={}, channel={}", eventId, channel);
            return new NotificationDispatchResult(
                    logEntity.getId(), eventId, channel, recipient, NotificationStatus.DISPATCHED,
                    logEntity.getProviderMessageId(), null
            );
        }

        logEntity.setRenderedContent(message.content());
        logEntity.setTemplateCode(templateCode);
        NotificationLogEntity savedEntity = logRepository.saveAndFlush(logEntity);
        final UUID entityId = savedEntity.getId();

        // 2. Dispatch with Resilience4j Retry & CircuitBreaker
        Supplier<ProviderReceipt> dispatchSupplier = () -> {
            log.debug("Calling provider {} for eventId={}", channel, eventId);
            return provider.dispatch(recipient, message, metadata);
        };

        Supplier<ProviderReceipt> decorated = CircuitBreaker.decorateSupplier(
                circuitBreaker,
                Retry.decorateSupplier(retry, dispatchSupplier)
        );

        try {
            ProviderReceipt receipt = decorated.get();
            savedEntity.markDispatched(receipt.providerMessageId());
            savedEntity = logRepository.saveAndFlush(savedEntity);

            log.info("Notification successfully dispatched: id={}, eventId={}, channel={}, providerMsgId={}",
                    savedEntity.getId(), eventId, channel, receipt.providerMessageId());

            return new NotificationDispatchResult(
                    savedEntity.getId(), eventId, channel, recipient, NotificationStatus.DISPATCHED,
                    receipt.providerMessageId(), null
            );

        } catch (Exception e) {
            log.error("All retries exhausted or non-retryable failure for eventId={}, channel={}: {}",
                    eventId, channel, e.getMessage(), e);

            savedEntity.incrementRetryCount();
            savedEntity.markDeadLettered(e.getMessage());
            savedEntity = logRepository.saveAndFlush(savedEntity);

            // Forward to DLQ (AC-3)
            DeadLetterNotificationPayload dlqPayload = DeadLetterNotificationPayload.of(
                    eventId, eventType, channel, recipient, message.content(), e.getMessage(), savedEntity.getRetryCount()
            );
            dlqPublisher.publishToDlq(dlqPayload);

            return new NotificationDispatchResult(
                    savedEntity.getId(), eventId, channel, recipient, NotificationStatus.DEAD_LETTERED,
                    null, e.getMessage()
            );
        }
    }
}
