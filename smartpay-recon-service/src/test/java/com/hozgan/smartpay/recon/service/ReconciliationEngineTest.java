package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;
import com.hozgan.smartpay.recon.dto.ReconciliationSummary;
import com.hozgan.smartpay.recon.entity.BankStatementLineEntity;
import com.hozgan.smartpay.recon.repository.BankStatementLineRepository;
import com.hozgan.smartpay.recon.service.LedgerReconGrpcClient.LedgerTransactionDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReconciliationEngine}.
 *
 * <p>The gRPC client and repository are mocked; no Spring context or Docker required.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ReconciliationEngineTest {

    @Mock
    private LedgerReconGrpcClient ledgerClient;

    @Mock
    private BankStatementLineRepository lineRepository;

    @InjectMocks
    private ReconciliationEngine engine;

    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final String END_TO_END_ID = "E2E-FACT-0841";
    private static final Money GBP_975 = Money.of("975.00", "GBP");
    private static final Money GBP_950 = Money.of("950.00", "GBP");

    private BankStatementLineEntity unmatchedLine;

    @BeforeEach
    void setUp() {
        unmatchedLine = new BankStatementLineEntity(
                STATEMENT_ID,
                "STMT-REF-001",
                END_TO_END_ID,
                GBP_975,
                EntryType.DEBIT,
                LocalDate.of(2026, 9, 5)
        );
        when(lineRepository.findByStatementIdAndReconciliationStatus(
                STATEMENT_ID, ReconciliationStatus.UNMATCHED))
                .thenReturn(List.of(unmatchedLine));
    }

    // -------------------------------------------------------------------------
    // AC-2: Exact match → MATCHED
    // -------------------------------------------------------------------------

    @Test
    void reconcileStatement_exactMatch_transitionsToMatched() {
        String ledgerTxId = UUID.randomUUID().toString();
        when(ledgerClient.findByEndToEndId(END_TO_END_ID))
                .thenReturn(Optional.of(new LedgerTransactionDetail(
                        ledgerTxId, END_TO_END_ID, GBP_975, EntryType.DEBIT, "GBP")));

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.matchedLines()).isEqualTo(1);
        assertThat(summary.discrepancyLines()).isEqualTo(0);

        ArgumentCaptor<BankStatementLineEntity> captor = ArgumentCaptor.forClass(BankStatementLineEntity.class);
        verify(lineRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(captor.getValue().getMatchedEntryId()).isEqualTo(UUID.fromString(ledgerTxId));
    }

    // -------------------------------------------------------------------------
    // AC-3: Amount discrepancy → DISCREPANCY
    // -------------------------------------------------------------------------

    @Test
    void reconcileStatement_amountMismatch_transitionsToDiscrepancy() {
        when(ledgerClient.findByEndToEndId(END_TO_END_ID))
                .thenReturn(Optional.of(new LedgerTransactionDetail(
                        UUID.randomUUID().toString(), END_TO_END_ID, GBP_950, EntryType.DEBIT, "GBP")));

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.matchedLines()).isEqualTo(0);
        assertThat(summary.discrepancyLines()).isEqualTo(1);

        ArgumentCaptor<BankStatementLineEntity> captor = ArgumentCaptor.forClass(BankStatementLineEntity.class);
        verify(lineRepository).save(captor.capture());
        assertThat(captor.getValue().getReconciliationStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY);
        assertThat(captor.getValue().getMatchedEntryId()).isNull();
    }

    @Test
    void reconcileStatement_ledgerEntryNotFound_transitionsToDiscrepancy() {
        when(ledgerClient.findByEndToEndId(END_TO_END_ID)).thenReturn(Optional.empty());

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.matchedLines()).isEqualTo(0);
        assertThat(summary.discrepancyLines()).isEqualTo(1);
    }

    @Test
    void reconcileStatement_currencyMismatch_transitionsToDiscrepancy() {
        Money eurAmount = Money.of("975.00", "EUR");
        when(ledgerClient.findByEndToEndId(END_TO_END_ID))
                .thenReturn(Optional.of(new LedgerTransactionDetail(
                        UUID.randomUUID().toString(), END_TO_END_ID, eurAmount, EntryType.DEBIT, "EUR")));

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.discrepancyLines()).isEqualTo(1);
    }

    @Test
    void reconcileStatement_entryTypeMismatch_transitionsToDiscrepancy() {
        when(ledgerClient.findByEndToEndId(END_TO_END_ID))
                .thenReturn(Optional.of(new LedgerTransactionDetail(
                        UUID.randomUUID().toString(), END_TO_END_ID, GBP_975, EntryType.CREDIT, "GBP")));

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.discrepancyLines()).isEqualTo(1);
    }

    @Test
    void reconcileStatement_noUnmatchedLines_returnAllZeros() {
        when(lineRepository.findByStatementIdAndReconciliationStatus(
                STATEMENT_ID, ReconciliationStatus.UNMATCHED))
                .thenReturn(List.of());

        ReconciliationSummary summary = engine.reconcileStatement(STATEMENT_ID);

        assertThat(summary.totalLines()).isEqualTo(0);
        assertThat(summary.matchedLines()).isEqualTo(0);
        assertThat(summary.discrepancyLines()).isEqualTo(0);
    }
}
