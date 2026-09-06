package com.hozgan.smartpay.invoice.converter;

import com.hozgan.smartpay.common.model.enums.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleTypeConverterTest {

    private final VehicleTypeConverter converter = new VehicleTypeConverter();

    @ParameterizedTest(name = "{0} converts to DB code {1}")
    @CsvSource({
            "VAN, VAN",
            "LUTON, LUTON",
            "SEVEN_POINT_FIVE_TONNE, 7_5T",
            "ARTIC, ARTIC"
    })
    @DisplayName("Converts VehicleType to database column string")
    void convertToDatabaseColumn(VehicleType vehicleType, String expectedDbCode) {
        assertThat(converter.convertToDatabaseColumn(vehicleType)).isEqualTo(expectedDbCode);
    }

    @ParameterizedTest(name = "DB code {0} converts to {1}")
    @CsvSource({
            "VAN, VAN",
            "LUTON, LUTON",
            "7_5T, SEVEN_POINT_FIVE_TONNE",
            "ARTIC, ARTIC"
    })
    @DisplayName("Converts database column string to VehicleType")
    void convertToEntityAttribute(String dbCode, VehicleType expectedVehicleType) {
        assertThat(converter.convertToEntityAttribute(dbCode)).isEqualTo(expectedVehicleType);
    }

    @Test
    @DisplayName("Null and blank handling")
    void nullAndBlankHandling() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }
}
