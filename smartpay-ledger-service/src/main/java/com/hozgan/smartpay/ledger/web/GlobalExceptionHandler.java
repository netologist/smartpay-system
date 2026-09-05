package com.hozgan.smartpay.ledger.web;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Translates domain exceptions to RFC 7807 Problem Details responses.
 * No stack traces are leaked; error codes align with API contract.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://smartpay.internal/errors/";

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Handling InsufficientFundsException: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(BASE_URI + "insufficient-funds"));
        pd.setTitle("Insufficient Funds");
        pd.setDetail(ex.getMessage());
        pd.setProperty("errorCode", ex.errorCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Handling AccountNotFoundException: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create(BASE_URI + "account-not-found"));
        pd.setTitle("Account Not Found");
        pd.setDetail(ex.getMessage());
        pd.setProperty("errorCode", ex.errorCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(UnbalancedJournalTransactionException.class)
    public ProblemDetail handleUnbalancedJournal(UnbalancedJournalTransactionException ex) {
        log.error("Handling UnbalancedJournalTransactionException: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create(BASE_URI + "ledger-unbalanced"));
        pd.setTitle("Unbalanced Journal Transaction");
        pd.setDetail(ex.getMessage());
        pd.setProperty("errorCode", ex.errorCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
        log.warn("Handling CurrencyMismatchException: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setType(URI.create(BASE_URI + "currency-mismatch"));
        pd.setTitle("Currency Mismatch");
        pd.setDetail(ex.getMessage());
        pd.setProperty("errorCode", ex.errorCode());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error on request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create(BASE_URI + "validation-error"));
        pd.setTitle("Validation Error");
        pd.setDetail("Request body contains invalid fields");
        pd.setProperty("violations", ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation on request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create(BASE_URI + "constraint-violation"));
        pd.setTitle("Constraint Violation");
        pd.setDetail(ex.getMessage());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
