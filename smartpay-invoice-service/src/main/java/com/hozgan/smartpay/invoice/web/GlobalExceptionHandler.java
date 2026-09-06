package com.hozgan.smartpay.invoice.web;

import com.hozgan.smartpay.common.exception.DuplicateLoadException;
import com.hozgan.smartpay.common.exception.EntityNotFoundException;
import com.hozgan.smartpay.common.exception.InvalidEpodSignatureException;
import com.hozgan.smartpay.common.exception.InvoiceAlreadySettledException;
import com.hozgan.smartpay.invoice.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidEpodSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEpodSignature(InvalidEpodSignatureException ex) {
        log.warn("ePOD signature verification failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERR_INVALID_EPOD_SIGNATURE", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(DuplicateLoadException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLoad(DuplicateLoadException ex) {
        log.warn("Duplicate load detected: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ERR_DUPLICATE_LOAD", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(InvoiceAlreadySettledException.class)
    public ResponseEntity<ErrorResponse> handleInvoiceAlreadySettled(InvoiceAlreadySettledException ex) {
        log.warn("Settlement mutability lock violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("ERR_INVOICE_ALREADY_SETTLED", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ERR_ENTITY_NOT_FOUND", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERR_ILLEGAL_ARGUMENT", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERR_VALIDATION_FAILED", message, Instant.now()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("ERR_CONSTRAINT_VIOLATION", ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled error encountered", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("ERR_INTERNAL_SERVER_ERROR", "An unexpected error occurred", Instant.now()));
    }
}
