package com.hozgan.smartpay.risk.grpc;

import com.hozgan.smartpay.common.exception.BlacklistedEntityException;
import com.hozgan.smartpay.common.exception.RiskEvaluationException;
import com.hozgan.smartpay.common.exception.SmartpayDomainException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RiskGrpcExceptionHelper {

    private RiskGrpcExceptionHelper() {
    }

    public static StatusRuntimeException toStatusRuntimeException(Exception ex) {
        log.warn("Mapping exception to gRPC StatusRuntimeException: {}", ex.getMessage());

        if (ex instanceof StatusRuntimeException sre) {
            return sre;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof NullPointerException) {
            return Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }
        if (ex instanceof BlacklistedEntityException) {
            return Status.PERMISSION_DENIED
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }
        if (ex instanceof RiskEvaluationException) {
            return Status.FAILED_PRECONDITION
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }
        if (ex instanceof SmartpayDomainException sde) {
            return Status.FAILED_PRECONDITION
                    .withDescription(sde.errorCode() + ": " + sde.getMessage())
                    .withCause(sde)
                    .asRuntimeException();
        }

        return Status.INTERNAL
                .withDescription("Internal error during risk evaluation: " + ex.getMessage())
                .withCause(ex)
                .asRuntimeException();
    }
}
