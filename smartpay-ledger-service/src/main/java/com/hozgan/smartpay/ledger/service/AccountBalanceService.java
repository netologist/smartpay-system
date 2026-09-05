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
public class AccountBalanceService {

    private final AccountRepository accountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final JournalTransactionRepository journalTransactionRepository;
    private final LedgerDomainService ledgerDomainService;

    public AccountBalanceService(
            AccountRepository accountRepository,
            AccountBalanceRepository accountBalanceRepository,
            JournalTransactionRepository journalTransactionRepository,
            LedgerDomainService ledgerDomainService) {
        this.accountRepository = accountRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.journalTransactionRepository = journalTransactionRepository;
        this.ledgerDomainService = ledgerDomainService;
    }

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

        // --- Idempotency short-circuit ---
        Optional<JournalTransactionEntity> existing =
                journalTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            JournalTransactionEntity cached = existing.get();
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
            throw new CurrencyMismatchException(srcCurrency, tgtCurrency);
        }
        if (!srcCurrency.equals(amount.currency())) {
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
        AccountEntity account = requireAccount(accountId);
        Currency currency = Currency.getInstance(account.getCurrency());

        if (!currency.equals(amount.currency())) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }

        AccountBalanceEntity balance = requireBalanceWithLock(accountId);
        Money available = balance.getAvailableBalance(currency);

        if (available.isLessThan(amount)) {
            throw new InsufficientFundsException(AccountId.of(accountId), amount, available);
        }

        balance.setHoldBalancePence(balance.getHoldBalancePence() + amount.toMinorUnits());
        balance.setUpdatedAt(Instant.now());
        return accountBalanceRepository.save(balance);
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

            ledgerDomainService.recordTransfer(
                    sourceAccountId, targetAccountId, amount,
                    referenceType, referenceId, idempotencyKey,
                    "Hold capture: " + referenceId);
        } else {
            balance.setUpdatedAt(Instant.now());
            accountBalanceRepository.save(balance);
        }

        return balance;
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
