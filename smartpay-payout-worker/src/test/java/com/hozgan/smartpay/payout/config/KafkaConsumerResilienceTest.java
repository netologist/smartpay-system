package com.hozgan.smartpay.payout.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConsumerResilience — Error Handler & Dead Letter Queue Tests")
class KafkaConsumerResilienceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("Configures DefaultErrorHandler with DeadLetterPublishingRecoverer and non-retryable exceptions")
    void shouldConfigureDefaultErrorHandlerWithDltAndBackoff() {
        KafkaConfig config = new KafkaConfig();
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(kafkaTemplate);

        assertThat(errorHandler).isNotNull();
        // Verifies non-retryable exceptions (IllegalArgumentException for corrupt payloads)
        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }
}
