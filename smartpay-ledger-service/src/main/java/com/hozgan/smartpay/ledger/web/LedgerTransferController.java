package com.hozgan.smartpay.ledger.web;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.ledger.dto.TransferRequest;
import com.hozgan.smartpay.ledger.dto.TransferResponse;
import com.hozgan.smartpay.ledger.dto.TransferResponse.MoneyView;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Currency;
import java.util.UUID;

/**
 * REST controller for ledger management operations.
 *
 * <p>All endpoints require an {@code Idempotency-Key} request header. Requests without
 * this header return {@code 400 Bad Request} before service logic is invoked.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
public class LedgerTransferController {

    private final AccountBalanceService accountBalanceService;

    /**
     * POST /api/v1/ledger/transfers
     *
     * <p>Executes an atomic, deadlock-free, double-entry balance transfer between two accounts.
     *
     * @param idempotencyKey client-supplied deduplication key
     * @param request        transfer parameters (source, target, amount, reference)
     * @return {@code 200 OK} with {@link TransferResponse} containing updated balances
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid TransferRequest request) {

        log.info("Received transfer request: idempotencyKey={}, sourceId={}, targetId={}, amountInPence={}, currency={}",
                idempotencyKey, request.sourceAccountId(), request.targetAccountId(),
                request.amountInPence(), request.currency());

        Currency currency = Currency.getInstance(request.currency());
        Money amount = Money.ofMinor(request.amountInPence(), currency);

        TransferResult result = accountBalanceService.transfer(
                UUID.fromString(request.sourceAccountId()),
                UUID.fromString(request.targetAccountId()),
                amount,
                request.referenceType(),
                request.referenceId(),
                idempotencyKey,
                request.description());

        log.info("Transfer processed: transactionId={}, status={}", result.transactionId(), result.status());

        return ResponseEntity.ok(toResponse(request, result, currency));
    }

    /**
     * GET /api/v1/ledger/accounts/{accountId}/balance
     *
     * <p>Returns the current cleared, hold, and available balances for the given account.
     *
     * @param accountId target account UUID
     * @return {@code 200 OK} with balance fields in pence
     */
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        log.debug("Fetching balance for account {}", accountId);
        var balance = accountBalanceService.getBalance(accountId);
        return ResponseEntity.ok(new BalanceResponse(
                accountId.toString(),
                balance.getClearedBalancePence(),
                balance.getHoldBalancePence(),
                balance.getAvailableBalancePence()
        ));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private TransferResponse toResponse(TransferRequest req, TransferResult result, Currency currency) {
        return new TransferResponse(
                result.transactionId().toString(),
                result.status().name(),
                req.sourceAccountId(),
                req.targetAccountId(),
                toMoneyView(result.amount()),
                toMoneyView(result.sourceAvailableBalance()),
                toMoneyView(result.targetAvailableBalance()),
                result.postedAt()
        );
    }

    private MoneyView toMoneyView(Money money) {
        return new MoneyView(
                money.currency().getCurrencyCode(),
                money.amount().toPlainString(),
                money.toMinorUnits()
        );
    }

    // -------------------------------------------------------------------------
    // Inner response record (balance query)
    // -------------------------------------------------------------------------

    public record BalanceResponse(
            String accountId,
            long clearedBalancePence,
            long holdBalancePence,
            long availableBalancePence
    ) {}
}
