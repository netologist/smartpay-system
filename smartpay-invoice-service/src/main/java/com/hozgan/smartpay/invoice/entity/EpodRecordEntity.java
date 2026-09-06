package com.hozgan.smartpay.invoice.entity;

import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.util.UuidV7;
import com.hozgan.smartpay.common.converter.CarrierIdConverter;
import com.hozgan.smartpay.common.converter.LoadIdConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "epod_records")
public class EpodRecordEntity {

    @Id
    private UUID id;

    @Convert(converter = LoadIdConverter.class)
    @Column(name = "load_id", unique = true, nullable = false, length = 64)
    private LoadId loadId;

    @Convert(converter = CarrierIdConverter.class)
    @Column(name = "carrier_id", nullable = false)
    private CarrierId carrierId;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "photo_s3_url", nullable = false, length = 512)
    private String photoS3Url;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EpodRecordEntity() {
        this.id = UuidV7.generate();
    }

    public EpodRecordEntity(LoadId loadId,
                            CarrierId carrierId,
                            Instant deliveredAt,
                            GeoLocation location,
                            String photoS3Url,
                            String signatureHash,
                            boolean verified) {
        this.id = UuidV7.generate();
        this.loadId = loadId;
        this.carrierId = carrierId;
        this.deliveredAt = deliveredAt;
        this.latitude = location.latitude();
        this.longitude = location.longitude();
        this.photoS3Url = photoS3Url;
        this.signatureHash = signatureHash;
        this.verified = verified;
        this.createdAt = Instant.now();
    }

    public void markVerified() {
        this.verified = true;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LoadId getLoadId() {
        return loadId;
    }

    public void setLoadId(LoadId loadId) {
        this.loadId = loadId;
    }

    public CarrierId getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(CarrierId carrierId) {
        this.carrierId = carrierId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public GeoLocation getLocation() {
        return new GeoLocation(latitude, longitude);
    }

    public void setLocation(GeoLocation location) {
        this.latitude = location.latitude();
        this.longitude = location.longitude();
    }

    public String getPhotoS3Url() {
        return photoS3Url;
    }

    public void setPhotoS3Url(String photoS3Url) {
        this.photoS3Url = photoS3Url;
    }

    public String getSignatureHash() {
        return signatureHash;
    }

    public void setSignatureHash(String signatureHash) {
        this.signatureHash = signatureHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
