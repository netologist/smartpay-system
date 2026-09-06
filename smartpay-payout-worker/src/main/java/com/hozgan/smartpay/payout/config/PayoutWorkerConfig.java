package com.hozgan.smartpay.payout.config;

import com.hozgan.smartpay.common.jackson.EntityIdJsonComponent;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.common.model.id.TransactionId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
@EnableConfigurationProperties(PayoutWorkerProperties.class)
@EnableAsync
public class PayoutWorkerConfig {

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

        return JsonMapper.builder()
                .addModule(module)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return createObjectMapper();
    }
}
