package com.hozgan.smartpay.notification.service;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.notification.config.NotificationProperties;
import com.hozgan.smartpay.notification.model.DeadLetterNotificationPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationDlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDlqPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public NotificationDlqPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    NotificationProperties properties,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publishToDlq(DeadLetterNotificationPayload payload) {
        String dlqTopic = properties.topics().dlq();
        String key = payload.eventId().toString();

        log.warn("Forwarding dead-lettered notification to DLQ topic={}: eventId={}, channel={}, error={}",
                dlqTopic, payload.eventId(), payload.channel(), payload.errorMessage());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(dlqTopic, key, json);
            log.info("Successfully published dead-letter notification to DLQ: key={}", key);
        } catch (Exception e) {
            log.error("Failed to publish notification to DLQ topic {}: {}", dlqTopic, e.getMessage(), e);
            // Fallback direct send
            kafkaTemplate.send(dlqTopic, key, payload);
        }
    }
}
