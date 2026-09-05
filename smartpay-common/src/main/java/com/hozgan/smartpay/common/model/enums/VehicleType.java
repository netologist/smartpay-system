package com.hozgan.smartpay.common.model.enums;

public enum VehicleType {
    VAN,
    LUTON,
    SEVEN_POINT_FIVE_TONNE("7_5T"),
    ARTIC("ARTIC");

    private final String dbCode;

    VehicleType() {
        this.dbCode = name();
    }

    VehicleType(String dbCode) {
        this.dbCode = dbCode;
    }

    public String dbCode() {
        return dbCode;
    }

    public static VehicleType fromDbCode(String code) {
        if ("7_5T".equalsIgnoreCase(code)) {
            return SEVEN_POINT_FIVE_TONNE;
        }
        for (VehicleType vt : values()) {
            if (vt.dbCode.equalsIgnoreCase(code) || vt.name().equalsIgnoreCase(code)) {
                return vt;
            }
        }
        throw new IllegalArgumentException("Unknown vehicle type dbCode: " + code);
    }
}
