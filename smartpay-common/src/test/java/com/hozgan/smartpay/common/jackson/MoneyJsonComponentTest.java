package com.hozgan.smartpay.common.jackson;

import com.hozgan.smartpay.common.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyJsonComponentTest {

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        SimpleModule module = new SimpleModule("MoneyModule");
        module.addSerializer(Money.class, new MoneyJsonComponent.Serializer());
        module.addDeserializer(Money.class, new MoneyJsonComponent.Deserializer());

        mapper = JsonMapper.builder()
                .addModule(module)
                .build();
    }

    @Test
    @DisplayName("Serializes Money into structured JSON object with amount, currency, and minorUnits")
    void serializeMoney() throws Exception {
        Money money = Money.ofGBP("525.00");
        String json = mapper.writeValueAsString(money);

        assertThat(json).contains("\"amount\":\"525.00\"");
        assertThat(json).contains("\"currency\":\"GBP\"");
        assertThat(json).contains("\"minorUnits\":52500");
    }

    @Test
    @DisplayName("Deserializes Money from structured JSON object with amount and currency")
    void deserializeMoneyFromObject() throws Exception {
        String json = "{\"amount\":\"705.60\",\"currency\":\"GBP\"}";
        Money money = mapper.readValue(json, Money.class);

        assertThat(money).isEqualTo(Money.ofGBP("705.60"));
        assertThat(money.toMinorUnits()).isEqualTo(70560L);
    }

    @Test
    @DisplayName("Deserializes Money from JSON object with minorUnits")
    void deserializeMoneyFromMinorUnits() throws Exception {
        String json = "{\"minorUnits\":6300,\"currency\":\"GBP\"}";
        Money money = mapper.readValue(json, Money.class);

        assertThat(money).isEqualTo(Money.ofGBP("63.00"));
        assertThat(money.toMinorUnits()).isEqualTo(6300L);
    }

    @Test
    @DisplayName("Deserializes Money from String 'EUR 350.00'")
    void deserializeMoneyFromString() throws Exception {
        String json = "\"EUR 350.00\"";
        Money money = mapper.readValue(json, Money.class);

        assertThat(money).isEqualTo(Money.ofEUR("350.00"));
        assertThat(money.toMinorUnits()).isEqualTo(35000L);
    }

    @Test
    @DisplayName("Deserializes Money from numeric value 120.50 (defaults to GBP)")
    void deserializeMoneyFromNumber() throws Exception {
        String json = "120.50";
        Money money = mapper.readValue(json, Money.class);

        assertThat(money).isEqualTo(Money.ofGBP("120.50"));
        assertThat(money.toMinorUnits()).isEqualTo(12050L);
    }
}
