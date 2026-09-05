package com.hozgan.smartpay.ledger.service;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.AccountBalanceRepository;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration service for atomic balance transfers with pessimistic locking.
 *
 * <h2>Deadlock prevention</h2>
 * All lock acquisitions on {@code account_balances} use <strong>lexicographic UUID ordering</strong>:
 * the account with the smaller UUID string is always locked first, regardless of which is source
 * or target. This total ordering eliminates the cyclic-wait condition required for a deadlock.
 *
 * <h2>Idempotency</h2>
 * If a {@code journal_transactions} row with the given {@code idempotencyKey} already exists
 * (status {@code POSTED}), the existing transaction is returned immediately without touching
 * balances or inserting new rows.
 *
 * <h2>Transaction boundary &amp; PostgreSQL concurrency</h2>
 * Uses {@code @Transactional} (default {@code READ_COMMITTED} in PostgreSQL). With pessimistic
 * {@code SELECT ... FOR UPDATE}, when two concurrent transactions attempt to lock the same row,
 * the second transaction queues behind the first. Once the first transaction commits, PostgreSQL's
 * {@code READ_COMMITTED} re-evaluates the query with the newly committed state, ensuring that the
 * second transaction sees the updated balance and throws {@link InsufficientFundsException} rather
 * than a serialization failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountBalanceService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final JournalTransactionRepository journalTransactionRepository;
    private final LedgerDomainService ledgerDomainService;

    // =========================================================================
    // TRANSFER
    // =========================================================================

    /**
     * Executes an atomic, deadlock-free, double-entry balance transfer.
     *
     * @return the persisted {@link TransferResult} containing the updated balances
     */
    @Transactional
    public TransferResult transfer(
            UUID sourceAccountId,
            UUID targetAccountId,
            Money amount,
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String description) {

        log.debug("Initiating transfer: {} -> {}, amount={}, idempKey={}",
                sourceAccountId, targetAccountId, amount, idempotencyKey);

        // --- Idempotency short-circuit ---
        Optional<JournalTransactionEntity> existing =
                journalTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            JournalTransactionEntity cached = existing.get();
            log.info("Idempotent cache hit for transaction [{}] with key [{}]", cached.getId(), idempotencyKey);
            AccountBalanceEntity srcBal = requireBalance(sourceAccountId);
            AccountBalanceEntity tgtBal = requireBalance(targetAccountId);
            Currency currency = resolveCurrency(sourceAccountId);
            return buildResult(cached, srcBal, tgtBal, amount, currency);
        }

        // --- Resolve accounts (existence check before locking) ---
        AccountEntity srcAccount = requireAccount(sourceAccountId);
        AccountEntity tgtAccount = requireAccount(targetAccountId);

        Currency srcCurrency = Currency.getInstance(srcAccount.getCurrency());
        Currency tgtCurrency = Currency.getInstance(tgtAccount.getCurrency());

        if (!srcCurrency.equals(tgtCurrency)) {
            log.warn("Currency mismatch between source account {} ({}) and target account {} ({})",
                    sourceAccountId, srcCurrency, targetAccountId, tgtCurrency);
            throw new CurrencyMismatchException(srcCurrency, tgtCurrency);
        }
        if (!srcCurrency.equals(amount.currency())) {
            log.warn("Transfer currency {} does not match source account currency {}",
                    amount.currency(), srcCurrency);
            throw new CurrencyMismatchException(srcCurrency, amount.currency());
        }

        // --- Deadlock-free ordered locking ---
        UUID firstLock  = min(sourceAccountId, targetAccountId);
        UUID secondLock = max(sourceAccountId, targetAccountId);

        AccountBalanceEntity first  = requireBalanceWithLock(firstLock);
        AccountBalanceEntity second = requireBalanceWithLock(secondLock);

        AccountBalanceEntity srcBal = firstLock.equals(sourceAccountId) ? first : second;
        AccountBalanceEntity tgtBal = firstLock.equals(targetAccountId) ? first : second;

        // --- Invariant: sufficient available balance ---
        Money available = srcBal.getAvailableBalance(srcCurrency);
        if (available.isLessThan(amount)) {
            log.warn("Insufficient funds for account {}. Available: {}, Requested: {}",
                    sourceAccountId, available, amount);
            throw new InsufficientFundsException(AccountId.of(sourceAccountId), amount, available);
        }

        // --- Balance mutation ---
        srcBal.setClearedBalancePence(srcBal.getClearedBalancePence() - amount.toMinorUnits());
        srcBal.setUpdatedAt(Instant.now());

        tgtBal.setClearedBalancePence(tgtBal.getClearedBalancePence() + amount.toMinorUnits());
        tgtBal.setUpdatedAt(Instant.now());

        accountBalanceRepository.save(srcBal);
        accountBalanceRepository.save(tgtBal);

        // --- Double-entry journal ---
        JournalTransactionEntity tx = ledgerDomainService.recordTransfer(
                sourceAccountId, targetAccountId, amount,
                referenceType, referenceId, idempotencyKey, description);

        log.info("Transfer completed successfully: txId={}, sourceId={}, targetId={}, amount={}",
                tx.getId(), sourceAccountId, targetAccountId, amount);

        return buildResult(tx, srcBal, tgtBal, amount, srcCurrency);
    }

    // =========================================================================
    // HOLD
    // =========================================================================

    /**
     * Reserves funds by incrementing {@code hold_balance_pence}, reducing available balance
     * without touching the cleared balance.
     *
     * @return account balance entity after hold
     */
    @Transactional
    public AccountBalanceEntity holdFunds(UUID accountId, Money amount) {
        log.debug("Holding funds for account {}: amount={}", accountId, amount);

        AccountEntity account = requireAccount(accountId);
        Currency currency = Currency.getInstance(account.getCurrency());

        if (!currency.equals(amount.currency())) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }

        AccountBalanceEntity balance = requireBalanceWithLock(accountId);
        Money available = balance.getAvailableBalance(currency);

        if (available.isLessThan(amount)) {
            log.warn("Insufficient funds to place hold on account {}. Available: {}, Requested: {}",
                    accountId, available, amount);
            throw new InsufficientFundsException(AccountId.of(accountId), amount, available);
        }

        balance.setHoldBalancePence(balance.getHoldBalancePence() + amount.toMinorUnits());
        balance.setUpdatedAt(Instant.now());
        AccountBalanceEntity saved = accountBalanceRepository.save(balance);

        log.info("Held {} on account {}. New hold balance: {} pence",
                amount, accountId, saved.getHoldBalancePence());

        return saved;
    }

    // =========================================================================
    // RELEASE HOLD
    // =========================================================================

    /**
     * Releases a hold on funds.
     *
     * <ul>
     *   <li>If {@code capture = true}: held amount deducted from cleared balance
     *       and a double-entry journal is recorded.</li>
     *   <li>If {@code capture = false}: hold is cancelled, restoring available balance.</li>
     * </ul>
     */
    @Transactional
    public AccountBalanceEntity releaseHold(
            UUID sourceAccountId,
            UUID targetAccountId,
            Money amount,
            boolean capture,
            String referenceType,
            String referenceId,
            String idempotencyKey) {

        log.debug("Releasing hold on account {}: amount={}, capture={}, ref={}",
                sourceAccountId, amount, capture, referenceId);

        AccountEntity account = requireAccount(sourceAccountId);
        Currency currency = Currency.getInstance(account.getCurrency());

        AccountBalanceEntity balance = requireBalanceWithLock(sourceAccountId);

        // Decrement hold regardless of capture flag
        long newHold = balance.getHoldBalancePence() - amount.toMinorUnits();
        if (newHold < 0) {
            newHold = 0; // guard: never allow negative hold
        }
        balance.setHoldBalancePence(newHold);

        if (capture) {
            balance.setClearedBalancePence(balance.getClearedBalancePence() - amount.toMinorUnits());
            balance.setUpdatedAt(Instant.now());
            accountBalanceRepository.save(balance);
            if (targetAccountId != null) {
                ledgerDomainService.recordTransfer(
                        sourceAccountId, targetAccountId, amount,
                        referenceType != null ? referenceType : "HOLD_CAPTURE",
                        referenceId != null ? referenceId : "HC-" + UUID.randomUUID(),
                        idempotencyKey != null ? idempotencyKey : "IDEMP-" + UUID.randomUUID(),
                        "Hold capture: " + referenceId);
                log.info("Captured hold on account {}: amount={}, credited to {}",
                        sourceAccountId, amount, targetAccountId);
            } else {
                log.info("Captured hold on account {}: amount={} deducted from cleared balance",
                        sourceAccountId, amount);
            }
        } else {
            balance.setUpdatedAt(Instant.now());
            accountBalanceRepository.save(balance);
            log.info("Cancelled hold on account {}: amount={}", sourceAccountId, amount);
        }

        return balance;
    }

    @Transactional
    public AccountBalanceEntity releaseHold(UUID sourceAccountId, Money amount, boolean capture) {
        return releaseHold(
                sourceAccountId, null, amount, capture,
                capture ? "HOLD_CAPTURE" : "HOLD_CANCEL",
                "HC-" + UUID.randomUUID(),
                "IDEMP-REL-" + UUID.randomUUID());
    }

    // =========================================================================
    // QUERY
    // =========================================================================

    @Transactional(readOnly = true)
    public AccountBalanceEntity getBalance(UUID accountId) {
        return requireBalance(accountId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private AccountEntity requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(AccountId.of(accountId)));
    }

    private AccountBalanceEntity requireBalance(UUID accountId) {
        return accountBalanceRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(AccountId.of(accountId)));
    }

    private AccountBalanceEntity requireBalanceWithLock(UUID accountId) {
        return accountBalanceRepository.findByAccountIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(AccountId.of(accountId)));
    }

    private Currency resolveCurrency(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(a -> Currency.getInstance(a.getCurrency()))
                .orElse(Money.GBP);
    }

    /** Lexicographic minimum — determines lock acquisition order. */
    private static UUID min(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Lexicographic maximum — determines lock acquisition order. */
    private static UUID max(UUID a, UUID b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private TransferResult buildResult(
            JournalTransactionEntity tx,
            AccountBalanceEntity srcBal,
            AccountBalanceEntity tgtBal,
            Money amount,
            Currency currency) {
        return new TransferResult(
                tx.getId(),
                tx.getStatus(),
                tx.getPostedAt(),
                srcBal.getAvailableBalance(currency),
                tgtBal.getAvailableBalance(currency),
                amount
        );
    }

    // =========================================================================
    // Inner result record
    // =========================================================================

    public record TransferResult(
            UUID transactionId,
            com.hozgan.smartpay.common.model.enums.JournalStatus status,
            Instant postedAt,
            Money sourceAvailableBalance,
            Money targetAvailableBalance,
            Money amount
    ) {}
}
