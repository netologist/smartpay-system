package com.hozgan.smartpay.ledger.service;

import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Currency;
import java.util.Optional;
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
@RequiredArgsConstructor
@Slf4j
public class LedgerDomainService {

    private final JournalTransactionRepository journalTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;

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
     * @throws UnbalancedJournalTransactionException if debit != credit
     */
    public JournalTransactionEntity recordTransfer(
            UUID sourceAccountId,
            UUID targetAccountId,
            Money amount,
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String description) {

        log.debug("Recording double-entry transfer: {} -> {}, amount={}, refType={}, refId={}",
                sourceAccountId, targetAccountId, amount, referenceType, referenceId);

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

        log.info("Recorded double-entry transaction [{}] with 2 balanced entries for amount {}",
                tx.getId(), amount);

        return tx;
    }

    /**
     * Persists a double-entry journal entry for a hold capture.
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

    public record JournalEntryItem(UUID accountId, EntryType entryType, Money amount) {}

    /**
     * Records a multi-legged journal transaction enforcing the Luca Pacioli zero-sum invariant
     * across all debit and credit lines.
     */
    public JournalTransactionEntity recordJournalTransaction(
            String referenceType,
            String referenceId,
            String idempotencyKey,
            String description,
            List<JournalEntryItem> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Journal entries list cannot be empty");
        }

        Currency currency = entries.getFirst().amount().currency();
        Money totalDebit = Money.zero(currency);
        Money totalCredit = Money.zero(currency);

        for (JournalEntryItem entry : entries) {
            if (!entry.amount().currency().equals(currency)) {
                throw new CurrencyMismatchException(currency, entry.amount().currency());
            }
            if (entry.entryType() == EntryType.DEBIT) {
                totalDebit = totalDebit.plus(entry.amount());
            } else if (entry.entryType() == EntryType.CREDIT) {
                totalCredit = totalCredit.plus(entry.amount());
            }
        }

        assertZeroSum(totalDebit, totalCredit);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<JournalTransactionEntity> existing = journalTransactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent cache hit for journal transaction [{}] with key [{}]", existing.get().getId(), idempotencyKey);
                return existing.get();
            }
        }

        JournalTransactionEntity tx = new JournalTransactionEntity(
                referenceType, referenceId, idempotencyKey, JournalStatus.POSTED, description);
        journalTransactionRepository.save(tx);

        List<JournalEntryEntity> lineEntities = entries.stream()
                .map(e -> new JournalEntryEntity(
                        tx.getId(), e.accountId(), e.entryType(),
                        e.amount().toMinorUnits(), e.amount().currency().getCurrencyCode()))
                .toList();

        journalEntryRepository.saveAll(lineEntities);
        log.info("Recorded multi-entry transaction [{}] with {} entries for ref [{}]",
                tx.getId(), lineEntities.size(), referenceId);
        return tx;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Guards the double-entry zero-sum invariant.
     */
    void assertZeroSum(Money totalDebit, Money totalCredit) {
        if (!totalDebit.equals(totalCredit)) {
            log.error("Zero-sum invariant violated! Total Debit: {}, Total Credit: {}", totalDebit, totalCredit);
            throw new UnbalancedJournalTransactionException(totalDebit, totalCredit);
        }
    }
}
