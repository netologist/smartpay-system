package com.hozgan.smartpay.common.jackson;

import com.hozgan.smartpay.common.model.id.*;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.FromStringDeserializer;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Jackson 3 serializer and deserializers for strongly-typed {@link EntityId} types.
 * Supports both scalar string and wrapped object representations.
 */
@JacksonComponent
public class EntityIdJsonComponent {

    public static class EntityIdSerializer extends StdScalarSerializer<EntityId<?>> {

        @SuppressWarnings("unchecked")
        public EntityIdSerializer() {
            super((Class<EntityId<?>>) (Class<?>) EntityId.class, false);
        }

        @Override
        public void serialize(EntityId<?> value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.asString());
            }
        }
    }

    public static abstract class BaseIdDeserializer<T> extends FromStringDeserializer<T> {

        protected BaseIdDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        protected Object _deserializeFromOther(JsonParser p, DeserializationContext ctxt, JsonToken t) throws JacksonException {
            if (t == JsonToken.START_OBJECT) {
                String val = null;
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if ("value".equals(p.currentName())) {
                        p.nextToken();
                        val = p.getText();
                    } else {
                        p.nextToken();
                    }
                }
                if (val != null && !val.isBlank()) {
                    try {
                        return _deserialize(val, ctxt);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to deserialize EntityId: " + val, e);
                    }
                }
            }
            return super._deserializeFromOther(p, ctxt, t);
        }
    }

    public static class AccountIdDeserializer extends BaseIdDeserializer<AccountId> {
        public AccountIdDeserializer() {
            super(AccountId.class);
        }

        @Override
        protected AccountId _deserialize(String value, DeserializationContext ctxt) {
            return AccountId.of(value);
        }
    }

    public static class CarrierIdDeserializer extends BaseIdDeserializer<CarrierId> {
        public CarrierIdDeserializer() {
            super(CarrierId.class);
        }

        @Override
        protected CarrierId _deserialize(String value, DeserializationContext ctxt) {
            return CarrierId.of(value);
        }
    }

    public static class ShipperIdDeserializer extends BaseIdDeserializer<ShipperId> {
        public ShipperIdDeserializer() {
            super(ShipperId.class);
        }

        @Override
        protected ShipperId _deserialize(String value, DeserializationContext ctxt) {
            return ShipperId.of(value);
        }
    }

    public static class InvoiceIdDeserializer extends BaseIdDeserializer<InvoiceId> {
        public InvoiceIdDeserializer() {
            super(InvoiceId.class);
        }

        @Override
        protected InvoiceId _deserialize(String value, DeserializationContext ctxt) {
            return InvoiceId.of(value);
        }
    }

    public static class LoadIdDeserializer extends BaseIdDeserializer<LoadId> {
        public LoadIdDeserializer() {
            super(LoadId.class);
        }

        @Override
        protected LoadId _deserialize(String value, DeserializationContext ctxt) {
            return LoadId.of(value);
        }
    }

    public static class TransactionIdDeserializer extends BaseIdDeserializer<TransactionId> {
        public TransactionIdDeserializer() {
            super(TransactionId.class);
        }

        @Override
        protected TransactionId _deserialize(String value, DeserializationContext ctxt) {
            return TransactionId.of(value);
        }
    }

    public static class IdempotencyKeyDeserializer extends BaseIdDeserializer<IdempotencyKey> {
        public IdempotencyKeyDeserializer() {
            super(IdempotencyKey.class);
        }

        @Override
        protected IdempotencyKey _deserialize(String value, DeserializationContext ctxt) {
            return IdempotencyKey.of(value);
        }
    }

    public static class PaymentIdDeserializer extends BaseIdDeserializer<PaymentId> {
        public PaymentIdDeserializer() {
            super(PaymentId.class);
        }

        @Override
        protected PaymentId _deserialize(String value, DeserializationContext ctxt) {
            return PaymentId.of(value);
        }
    }

    public static class NotificationIdDeserializer extends BaseIdDeserializer<NotificationId> {
        public NotificationIdDeserializer() {
            super(NotificationId.class);
        }

        @Override
        protected NotificationId _deserialize(String value, DeserializationContext ctxt) {
            return NotificationId.of(value);
        }
    }
}
