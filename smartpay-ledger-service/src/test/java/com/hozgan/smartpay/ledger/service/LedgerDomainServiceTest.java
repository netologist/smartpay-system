package com.hozgan.smartpay.ledger.service;

import com.hozgan.smartpay.common.model.id.TransactionId;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit test for {@link LedgerDomainService}.
 *
 * <p>No Spring context, no database. Repositories are mocked via Mockito.
 * Verifies the double-entry zero-sum invariant and journal persistence contract.
 */
import org.junit.jupiter.api.Tag;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerDomainService — Unit Tests")
class LedgerDomainServiceTest {

    @Mock
    private JournalTransactionRepository journalTransactionRepository;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private LedgerDomainService ledgerDomainService;

    @Captor
    private ArgumentCaptor<JournalTransactionEntity> txCaptor;

    @Captor
    private ArgumentCaptor<List<JournalEntryEntity>> entriesCaptor;

    private UUID sourceId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        // lenient: assertZeroSum tests do not invoke repos — stubs must not fail those tests
        lenient().when(journalTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(journalEntryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================

    @Nested
    @DisplayName("recordTransfer — Happy Path")
    class RecordTransferHappyPath {

        @Test
        @DisplayName("persists transaction header with POSTED status")
        void persistsTransactionWithPostedStatus() {
            Money amount = Money.ofGBP("250.00");

            JournalTransactionEntity result = ledgerDomainService.recordTransfer(
                    sourceId, targetId, amount,
                    "INVOICE_SETTLEMENT", "INV-001", "IDEMP-001", "Test transfer");

            verify(journalTransactionRepository).save(txCaptor.capture());
            JournalTransactionEntity saved = txCaptor.getValue();

            assertThat(saved.getStatus()).isEqualTo(JournalStatus.POSTED);
            assertThat(saved.getIdempotencyKey()).isEqualTo(IdempotencyKey.of("IDEMP-001"));
            assertThat(saved.getReferenceType()).isEqualTo("INVOICE_SETTLEMENT");
            assertThat(saved.getReferenceId()).isEqualTo("INV-001");
        }

        @Test
        @DisplayName("persists exactly two journal entries: DEBIT source + CREDIT target")
        void persistsExactlyTwoEntries() {
            Money amount = Money.ofGBP("250.00");

            ledgerDomainService.recordTransfer(
                    sourceId, targetId, amount,
                    "INVOICE_SETTLEMENT", "INV-001", "IDEMP-001", "Test transfer");

            verify(journalEntryRepository).saveAll(entriesCaptor.capture());
            List<JournalEntryEntity> entries = entriesCaptor.getValue();

            assertThat(entries).hasSize(2);

            JournalEntryEntity debit  = entries.stream()
                    .filter(e -> e.getEntryType().name().equals("DEBIT")).findFirst().orElseThrow();
            JournalEntryEntity credit = entries.stream()
                    .filter(e -> e.getEntryType().name().equals("CREDIT")).findFirst().orElseThrow();

            assertThat(debit.getAccountId()).isEqualTo(AccountId.of(sourceId));
            assertThat(debit.getAmountInPence()).isEqualTo(25000L);
            assertThat(debit.getCurrency()).isEqualTo("GBP");

            assertThat(credit.getAccountId()).isEqualTo(AccountId.of(targetId));
            assertThat(credit.getAmountInPence()).isEqualTo(25000L);
            assertThat(credit.getCurrency()).isEqualTo("GBP");
        }

        @Test
        @DisplayName("both journal entries share the same transaction_id")
        void bothEntriesShareTransactionId() {
            Money amount = Money.ofGBP("100.00");

            ledgerDomainService.recordTransfer(
                    sourceId, targetId, amount,
                    "FACTORING_PAYOUT", "PAY-001", "IDEMP-002", "Payout");

            verify(journalEntryRepository).saveAll(entriesCaptor.capture());
            List<JournalEntryEntity> entries = entriesCaptor.getValue();

            TransactionId txId = entries.get(0).getTransactionId();
            assertThat(entries).allMatch(e -> e.getTransactionId().equals(txId));
        }

        @Test
        @DisplayName("zero-sum invariant holds: SUM(DEBIT) == SUM(CREDIT) for equal amounts")
        void zeroSumInvariantHolds() {
            Money amount = Money.ofGBP("999.99");

            ledgerDomainService.recordTransfer(
                    sourceId, targetId, amount,
                    "REVERSAL", "REV-001", "IDEMP-003", "Reversal");

            verify(journalEntryRepository).saveAll(entriesCaptor.capture());
            List<JournalEntryEntity> entries = entriesCaptor.getValue();

            long totalDebit  = entries.stream().filter(e -> e.getEntryType().name().equals("DEBIT"))
                    .mapToLong(JournalEntryEntity::getAmountInPence).sum();
            long totalCredit = entries.stream().filter(e -> e.getEntryType().name().equals("CREDIT"))
                    .mapToLong(JournalEntryEntity::getAmountInPence).sum();

            assertThat(totalDebit).isEqualTo(totalCredit);
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("assertZeroSum — Invariant Guard")
    class AssertZeroSumInvariant {

        @Test
        @DisplayName("does NOT throw when debit equals credit")
        void doesNotThrowWhenBalanced() {
            Money amount = Money.ofGBP("100.00");
            assertThatCode(() -> ledgerDomainService.assertZeroSum(amount, amount))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws UnbalancedJournalTransactionException when debit != credit")
        void throwsWhenUnbalanced() {
            Money debit  = Money.ofGBP("250.00");
            Money credit = Money.ofGBP("240.00");

            assertThatThrownBy(() -> ledgerDomainService.assertZeroSum(debit, credit))
                    .isInstanceOf(UnbalancedJournalTransactionException.class)
                    .satisfies(ex -> assertThat(((UnbalancedJournalTransactionException) ex).errorCode())
                            .isEqualTo("ERR_LEDGER_UNBALANCED"))
                    .hasMessageContaining("250.00")
                    .hasMessageContaining("240.00");
        }

        @Test
        @DisplayName("error message includes the exact penny difference")
        void errorMessageIncludesDifference() {
            Money debit  = Money.ofGBP("100.50");
            Money credit = Money.ofGBP("100.00");

            assertThatThrownBy(() -> ledgerDomainService.assertZeroSum(debit, credit))
                    .isInstanceOf(UnbalancedJournalTransactionException.class)
                    .hasMessageContaining("0.50"); // difference
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("recordTransfer — Minor Currency Units")
    class MinorUnitPrecision {

        @Test
        @DisplayName("penny-precise: GBP 1000.00 stored as 100000 pence")
        void gbpAmountStoredAsPence() {
            Money amount = Money.ofGBP("1000.00");

            ledgerDomainService.recordTransfer(
                    sourceId, targetId, amount,
                    "BATCH", "BATCH-001", "IDEMP-004", "Batch settlement");

            verify(journalEntryRepository).saveAll(entriesCaptor.capture());
            assertThat(entriesCaptor.getValue())
                    .extracting(JournalEntryEntity::getAmountInPence)
                    .containsExactly(100000L, 100000L);
        }
    }
}
