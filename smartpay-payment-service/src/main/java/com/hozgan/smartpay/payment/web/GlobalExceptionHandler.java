package com.hozgan.smartpay.payment.web;

import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.InsufficientFundsForPaymentException;
import com.hozgan.smartpay.common.exception.PaymentNotFoundException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.payment.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
        log.warn("Idempotency conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(RequestHashMismatchException.class)
    public ResponseEntity<ErrorResponse> handleHashMismatch(RequestHashMismatchException ex) {
        log.warn("Request hash mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InsufficientFundsForPaymentException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsForPaymentException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("ERR_VALIDATION", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("ERR_VALIDATION", ex.getMessage()));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("ERR_VALIDATION", ex.getMessage()));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("ERR_INTERNAL", "An unexpected error occurred"));
    }
}
