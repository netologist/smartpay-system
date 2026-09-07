package com.hozgan.smartpay.common.jackson;

import com.hozgan.smartpay.common.model.id.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("EntityIdJsonComponent — JSON Serialization & Deserialization Tests")
class EntityIdJsonComponentTest {

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
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

        mapper = JsonMapper.builder()
                .addModule(module)
                .build();
    }

    @Test
    @DisplayName("Serializes strongly-typed IDs into scalar JSON strings")
    void serializeIds() throws Exception {
        UUID uuid = UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512");
        AccountId accountId = AccountId.of(uuid);
        LoadId loadId = LoadId.of("LOAD-2026-UK-0841");

        assertThat(mapper.writeValueAsString(accountId)).isEqualTo("\"0191c7a2-9b24-7f11-9a1c-3d842b10a512\"");
        assertThat(mapper.writeValueAsString(loadId)).isEqualTo("\"LOAD-2026-UK-0841\"");
    }

    @Test
    @DisplayName("Deserializes scalar JSON strings into strongly-typed IDs")
    void deserializeIds() throws Exception {
        AccountId accountId = mapper.readValue("\"0191c7a2-9b24-7f11-9a1c-3d842b10a512\"", AccountId.class);
        assertThat(accountId.value()).isEqualTo(UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"));

        LoadId loadId = mapper.readValue("\"LOAD-2026-UK-0841\"", LoadId.class);
        assertThat(loadId.value()).isEqualTo("LOAD-2026-UK-0841");

        CarrierId carrierId = mapper.readValue("\"0191c7a2-9b24-7f11-9a1c-3d842b10a512\"", CarrierId.class);
        assertThat(carrierId.value()).isEqualTo(UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"));

        PaymentId paymentId = mapper.readValue("\"0191c7a2-9b24-7f11-9a1c-3d842b10a512\"", PaymentId.class);
        assertThat(paymentId.value()).isEqualTo(UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"));

        NotificationId notificationId = mapper.readValue("\"0191c7a2-9b24-7f11-9a1c-3d842b10a512\"", NotificationId.class);
        assertThat(notificationId.value()).isEqualTo(UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"));
    }
}
