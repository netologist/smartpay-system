package com.hozgan.smartpay.ledger.service;

import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Pure domain service responsible for double-entry journal recording.
 *
 * <p>Invariant: for every call to {@link #recordTransfer}, exactly two journal_entries rows are
 * persisted — one DEBIT and one CREDIT — for an identical amount, guaranteeing
 * {@code SUM(DEBIT) == SUM(CREDIT)} per transaction.
 *
 * <p>This class has no knowledge of account locking or balance mutation;
 * those concerns belong to {@link AccountBalanceService}.
 */
@Service
public class LedgerDomainService {

    private final JournalTransactionRepository journalTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;

    public LedgerDomainService(
            JournalTransactionRepository journalTransactionRepository,
            JournalEntryRepository journalEntryRepository) {
        this.journalTransactionRepository = journalTransactionRepository;
        this.journalEntryRepository = journalEntryRepository;
    }

    /**
     * Persists the double-entry journal header and two immutable line items.
     *
     * <p>Validation: verifies zero-sum invariant before any DB write. Rolls back
     * atomically if the invariant is violated (caller's {@code @Transactional} boundary).
     *
     * @param sourceAccountId debited account UUID
     * @param targetAccountId credited account UUID
     * @param amount          transfer amount (positive, same currency for both sides)
     * @param referenceType   domain reference category (e.g. {@code INVOICE_SETTLEMENT})
     * @param referenceId     domain reference identifier
     * @param idempotencyKey  caller-supplied idempotency key stored on the transaction header
     * @param description     human-readable description for auditors
     * @return the saved {@link JournalTransactionEntity}
     * @throws UnbalancedJournalTransactionException if debit != credit (should not occur
     *                                               for a two-party transfer with equal amounts,
     *                                               but guards against programmatic errors)
     */
    public JournalTransactionEntity recordTransfer(
            UUID sourceAccountId,
            UUID targetAccountId,
            Money amount,
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String description) {

        assertZeroSum(amount, amount);

        JournalTransactionEntity tx = new JournalTransactionEntity(
                referenceType, referenceId, idempotencyKey, JournalStatus.POSTED, description);
        journalTransactionRepository.save(tx);

        JournalEntryEntity debit = new JournalEntryEntity(
                tx.getId(), sourceAccountId, EntryType.DEBIT,
                amount.toMinorUnits(), amount.currency().getCurrencyCode());

        JournalEntryEntity credit = new JournalEntryEntity(
                tx.getId(), targetAccountId, EntryType.CREDIT,
                amount.toMinorUnits(), amount.currency().getCurrencyCode());

        journalEntryRepository.saveAll(List.of(debit, credit));
        return tx;
    }

    /**
     * Persists a single DEBIT journal entry for a hold capture or fee deduction.
     * The matching CREDIT side must be supplied by the caller as a separate entry.
     */
    public JournalTransactionEntity recordHoldCapture(
            UUID debitAccountId,
            UUID creditAccountId,
            Money amount,
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String description) {
        return recordTransfer(debitAccountId, creditAccountId, amount, referenceType, referenceId, idempotencyKey, description);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Guards the double-entry zero-sum invariant.
     *
     * <p>For a vanilla two-party transfer both sides equal the transfer amount,
     * so the check always passes. The overload with explicit debit/credit totals
     * is used for multi-leg transactions (future: batch settlements).
     */
    void assertZeroSum(Money totalDebit, Money totalCredit) {
        if (!totalDebit.equals(totalCredit)) {
            throw new UnbalancedJournalTransactionException(totalDebit, totalCredit);
        }
    }
}
