package com.hozgan.smartpay.common.model;

import java.math.BigDecimal;
import java.util.Objects;

public record GeoLocation(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LAT = new BigDecimal("-90.0");
    private static final BigDecimal MAX_LAT = new BigDecimal("90.0");
    private static final BigDecimal MIN_LON = new BigDecimal("-180.0");
    private static final BigDecimal MAX_LON = new BigDecimal("180.0");

    public GeoLocation {
        Objects.requireNonNull(latitude, "latitude cannot be null");
        Objects.requireNonNull(longitude, "longitude cannot be null");

        if (latitude.compareTo(MIN_LAT) < 0 || latitude.compareTo(MAX_LAT) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude.compareTo(MIN_LON) < 0 || longitude.compareTo(MAX_LON) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, got: " + longitude);
        }
    }

    public static GeoLocation of(double latitude, double longitude) {
        return new GeoLocation(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    public static GeoLocation of(String latitude, String longitude) {
        return new GeoLocation(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
