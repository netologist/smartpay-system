package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.CarrierId;

public final class BlacklistedEntityException extends RiskException {

    private final CarrierId carrierId;
    private final String status;

    public BlacklistedEntityException(CarrierId carrierId, String status) {
        super("ERR_CARRIER_SANCTIONED_OR_BLACKLISTED",
                String.format("Carrier %s is %s and ineligible for factoring disbursements",
                        carrierId, status));
        this.carrierId = carrierId;
        this.status = status;
    }

    public CarrierId carrierId() {
        return carrierId;
    }

    public String status() {
        return status;
    }
}
