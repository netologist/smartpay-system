package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.LoadId;

public final class InvalidEpodSignatureException extends LogisticsException {

    private final LoadId loadId;

    public InvalidEpodSignatureException(LoadId loadId) {
        super("ERR_INVALID_EPOD_SIGNATURE",
                String.format("Electronic Proof of Delivery (ePOD) cryptographic signature verification failed for load %s", loadId));
        this.loadId = loadId;
    }

    public LoadId loadId() {
        return loadId;
    }
}
