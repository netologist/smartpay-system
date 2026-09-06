package com.hozgan.smartpay.invoice.config;

import com.hozgan.smartpay.common.model.enums.VehicleType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "smartpay.pricing")
public class FreightPricingProperties {

    private String defaultCurrency = "GBP";
    private BigDecimal fuelSurchargeRate = new BigDecimal("0.12"); // 12%
    private BigDecimal vatRate = new BigDecimal("0.20");           // 20%

    private Map<VehicleType, BigDecimal> ratesPerMile = new EnumMap<>(VehicleType.class);

    public FreightPricingProperties() {
        // Sensible industry defaults conforming to STORY-002 AC-2
        ratesPerMile.put(VehicleType.ARTIC, new BigDecimal("3.50"));
        ratesPerMile.put(VehicleType.SEVEN_POINT_FIVE_TONNE, new BigDecimal("2.50"));
        ratesPerMile.put(VehicleType.LUTON, new BigDecimal("2.00"));
        ratesPerMile.put(VehicleType.VAN, new BigDecimal("1.50"));
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public BigDecimal getFuelSurchargeRate() {
        return fuelSurchargeRate;
    }

    public void setFuelSurchargeRate(BigDecimal fuelSurchargeRate) {
        this.fuelSurchargeRate = fuelSurchargeRate;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public Map<VehicleType, BigDecimal> getRatesPerMile() {
        return ratesPerMile;
    }

    public void setRatesPerMile(Map<VehicleType, BigDecimal> ratesPerMile) {
        this.ratesPerMile = ratesPerMile;
    }

    public BigDecimal getRateForVehicle(VehicleType vehicleType) {
        BigDecimal rate = ratesPerMile.get(vehicleType);
        if (rate == null) {
            throw new IllegalArgumentException("No pricing rate configured for vehicle type: " + vehicleType);
        }
        return rate;
    }
}
