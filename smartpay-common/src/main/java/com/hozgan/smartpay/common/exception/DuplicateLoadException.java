package com.hozgan.smartpay.common.exception;

import com.hozgan.smartpay.common.model.id.LoadId;

public final class DuplicateLoadException extends LogisticsException {

    private final LoadId loadId;

    public DuplicateLoadException(LoadId loadId) {
        super("ERR_DUPLICATE_LOAD", "Load already exists in the system: " + loadId);
        this.loadId = loadId;
    }

    public LoadId loadId() {
        return loadId;
    }
}
