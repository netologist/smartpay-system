package com.hozgan.smartpay.invoice.entity;

import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.util.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    private UUID id;

    @Column(name = "load_id", unique = true, nullable = false, length = 64)
    private String loadId;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 16)
    private VehicleType vehicleType;

    @Column(name = "mileage_miles", nullable = false, precision = 8, scale = 2)
    private BigDecimal mileageMiles;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "GBP";

    @Column(name = "base_amount_pence", nullable = false)
    private long baseAmountPence;

    @Column(name = "fuel_surcharge_pence", nullable = false)
    private long fuelSurchargePence = 0L;

    @Column(name = "vat_amount_pence", nullable = false)
    private long vatAmountPence = 0L;

    @Column(name = "total_amount_pence", nullable = false)
    private long totalAmountPence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private InvoiceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public InvoiceEntity() {
        this.id = UuidV7.generate();
    }

    public InvoiceEntity(String loadId, UUID shipperId, UUID carrierId, VehicleType vehicleType, BigDecimal mileageMiles, InvoicePricing pricing, InvoiceStatus status) {
        this.id = UuidV7.generate();
        this.loadId = loadId;
        this.shipperId = shipperId;
        this.carrierId = carrierId;
        this.vehicleType = vehicleType;
        this.mileageMiles = mileageMiles;
        this.currency = pricing.baseAmount().currency().getCurrencyCode();
        this.baseAmountPence = pricing.baseAmount().toMinorUnits();
        this.fuelSurchargePence = pricing.fuelSurcharge().toMinorUnits();
        this.vatAmountPence = pricing.vatAmount().toMinorUnits();
        this.totalAmountPence = pricing.totalAmount().toMinorUnits();
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getLoadId() {
        return loadId;
    }

    public void setLoadId(String loadId) {
        this.loadId = loadId;
    }

    public UUID getShipperId() {
        return shipperId;
    }

    public void setShipperId(UUID shipperId) {
        this.shipperId = shipperId;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(UUID carrierId) {
        this.carrierId = carrierId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public BigDecimal getMileageMiles() {
        return mileageMiles;
    }

    public void setMileageMiles(BigDecimal mileageMiles) {
        this.mileageMiles = mileageMiles;
    }

    public long getBaseAmountPence() {
        return baseAmountPence;
    }

    public void setBaseAmountPence(long baseAmountPence) {
        this.baseAmountPence = baseAmountPence;
    }

    public long getFuelSurchargePence() {
        return fuelSurchargePence;
    }

    public void setFuelSurchargePence(long fuelSurchargePence) {
        this.fuelSurchargePence = fuelSurchargePence;
    }

    public long getVatAmountPence() {
        return vatAmountPence;
    }

    public void setVatAmountPence(long vatAmountPence) {
        this.vatAmountPence = vatAmountPence;
    }

    public long getTotalAmountPence() {
        return totalAmountPence;
    }

    public void setTotalAmountPence(long totalAmountPence) {
        this.totalAmountPence = totalAmountPence;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public InvoicePricing getPricing() {
        java.util.Currency cur = java.util.Currency.getInstance(currency);
        return new InvoicePricing(
                Money.ofMinor(baseAmountPence, cur),
                Money.ofMinor(fuelSurchargePence, cur),
                Money.ofMinor(vatAmountPence, cur),
                Money.ofMinor(totalAmountPence, cur)
        );
    }
}
