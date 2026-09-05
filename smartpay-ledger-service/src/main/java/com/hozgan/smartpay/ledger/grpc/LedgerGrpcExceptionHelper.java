package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps domain exceptions from the ledger bounded context to standard gRPC {@link Status} codes.
 */
@Slf4j
public final class LedgerGrpcExceptionHelper {

    private LedgerGrpcExceptionHelper() {
        // utility class
    }

    public static StatusRuntimeException toStatusRuntimeException(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException sre) {
            return sre;
        }

        if (throwable instanceof AccountNotFoundException ex) {
            log.warn("Account not found in gRPC call: {}", ex.getMessage());
            return Status.NOT_FOUND
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }

        if (throwable instanceof InsufficientFundsException ex) {
            log.warn("Insufficient funds in gRPC call: {}", ex.getMessage());
            return Status.FAILED_PRECONDITION
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }

        if (throwable instanceof CurrencyMismatchException ex) {
            log.warn("Currency mismatch in gRPC call: {}", ex.getMessage());
            return Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }

        if (throwable instanceof UnbalancedJournalTransactionException ex) {
            log.warn("Unbalanced journal transaction in gRPC call: {}", ex.getMessage());
            return Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }

        if (throwable instanceof IllegalArgumentException ex) {
            log.warn("Invalid argument in gRPC call: {}", ex.getMessage());
            return Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .withCause(ex)
                    .asRuntimeException();
        }

        log.error("Unhandled internal exception during gRPC execution", throwable);
        return Status.INTERNAL
                .withDescription("Internal ledger service error: " + throwable.getMessage())
                .withCause(throwable)
                .asRuntimeException();
    }
}
