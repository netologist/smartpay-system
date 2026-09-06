package com.hozgan.smartpay.common.jackson;

import com.hozgan.smartpay.common.model.Money;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.jackson.ObjectValueDeserializer;
import org.springframework.boot.jackson.ObjectValueSerializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;

import java.util.Currency;

/**
 * Jackson 3 serializer and deserializer component for {@link Money} value objects.
 * Automatically registered via Spring Boot 4.1 {@link JacksonComponent}.
 */
@JacksonComponent
public class MoneyJsonComponent {

    public static class Serializer extends ObjectValueSerializer<Money> {

        @Override
        protected void serializeObject(Money value, JsonGenerator gen, SerializationContext context) {
            gen.writeStringProperty("amount", value.amount().toPlainString());
            gen.writeStringProperty("currency", value.currency().getCurrencyCode());
            gen.writeNumberProperty("minorUnits", value.toMinorUnits());
        }
    }

    public static class Deserializer extends ObjectValueDeserializer<Money> {

        @Override
        protected Money deserializeObject(JsonParser parser, DeserializationContext context, JsonNode rootNode) {
            if (rootNode == null || rootNode.isNull()) {
                return null;
            }

            // Case 1: String representation, e.g. "GBP 525.00" or "525.00"
            if (rootNode.isTextual()) {
                String text = rootNode.asText().trim();
                if (text.isEmpty()) {
                    return null;
                }
                String[] parts = text.split("\\s+");
                if (parts.length == 2) {
                    return Money.of(parts[1], parts[0]);
                }
                return Money.ofGBP(text);
            }

            // Case 2: Direct numeric value (defaults to GBP)
            if (rootNode.isNumber()) {
                return Money.ofGBP(rootNode.decimalValue());
            }

            // Case 3: JSON Object with fields {"amount": "525.00", "currency": "GBP"} or {"minorUnits": 52500, "currency": "GBP"}
            JsonNode currencyNode = rootNode.get("currency");
            Currency currency = (currencyNode != null && !currencyNode.isNull() && !currencyNode.asText().isBlank())
                    ? Currency.getInstance(currencyNode.asText().trim().toUpperCase())
                    : Money.GBP;

            JsonNode minorUnitsNode = rootNode.get("minorUnits");
            if (minorUnitsNode != null && !minorUnitsNode.isNull() && minorUnitsNode.isNumber()) {
                return Money.ofMinor(minorUnitsNode.asLong(), currency);
            }

            JsonNode amountNode = rootNode.get("amount");
            if (amountNode != null && !amountNode.isNull()) {
                if (amountNode.isNumber()) {
                    return Money.of(amountNode.decimalValue(), currency);
                }
                return Money.of(amountNode.asText().trim(), currency);
            }

            throw new IllegalArgumentException("Unable to deserialize Money from JSON node: " + rootNode);
        }
    }
}
