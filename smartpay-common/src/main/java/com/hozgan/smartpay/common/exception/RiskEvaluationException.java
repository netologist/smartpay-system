package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.CarrierId;

public final class RiskEvaluationException extends RiskException {

    private final CarrierId carrierId;

    public RiskEvaluationException(String errorCode, String message) {
        super(errorCode, message);
        this.carrierId = null;
    }

    public RiskEvaluationException(CarrierId carrierId, String errorCode, String message) {
        super(errorCode, message);
        this.carrierId = carrierId;
    }

    public CarrierId carrierId() {
        return carrierId;
    }
}
