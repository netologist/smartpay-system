package com.hozgan.smartpay.notification.config;
import com.hozgan.smartpay.common.jackson.MoneyJsonComponent;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.jackson.EntityIdJsonComponent;
import com.hozgan.smartpay.common.model.id.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private final String bootstrapServers;
    private final String consumerGroupId;
    private final String dlqTopic;

    public KafkaConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${smartpay.notification.consumer-group-id:smartpay-notification-workers}") String consumerGroupId,
            @Value("${smartpay.notification.topics.dlq:smartpay.events.notifications.dlq}") String dlqTopic) {
        this.bootstrapServers = bootstrapServers;
        this.consumerGroupId = consumerGroupId;
        this.dlqTopic = dlqTopic;
    }

    public static ObjectMapper createObjectMapper() {
        SimpleModule module = new SimpleModule("EntityIdModule");
        module.addSerializer(new EntityIdJsonComponent.EntityIdSerializer());
        module.addDeserializer(AccountId.class, new EntityIdJsonComponent.AccountIdDeserializer());
        module.addDeserializer(CarrierId.class, new EntityIdJsonComponent.CarrierIdDeserializer());
        module.addDeserializer(ShipperId.class, new EntityIdJsonComponent.ShipperIdDeserializer());
        module.addDeserializer(InvoiceId.class, new EntityIdJsonComponent.InvoiceIdDeserializer());
        module.addDeserializer(LoadId.class, new EntityIdJsonComponent.LoadIdDeserializer());
        module.addDeserializer(TransactionId.class, new EntityIdJsonComponent.TransactionIdDeserializer());
        module.addDeserializer(IdempotencyKey.class, new EntityIdJsonComponent.IdempotencyKeyDeserializer());
        module.addDeserializer(PaymentId.class, new EntityIdJsonComponent.PaymentIdDeserializer());
        module.addDeserializer(NotificationId.class, new EntityIdJsonComponent.NotificationIdDeserializer());

        SimpleModule moneyModule = new SimpleModule("MoneyModule");
        moneyModule.addSerializer(Money.class, new MoneyJsonComponent.Serializer());
        moneyModule.addDeserializer(Money.class, new MoneyJsonComponent.Deserializer());

        return JsonMapper.builder()
                .addModule(module)
                .addModule(moneyModule)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return createObjectMapper();
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(dlqTopic, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(200L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor(
                Thread.ofVirtual().name("kafka-notification-", 0).factory());
        factory.getContainerProperties().setListenerTaskExecutor(taskExecutor);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
