package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;
import com.hozgan.smartpay.recon.dto.ReconciliationSummary;
import com.hozgan.smartpay.recon.entity.BankStatementLineEntity;
import com.hozgan.smartpay.recon.repository.BankStatementLineRepository;
import com.hozgan.smartpay.recon.service.LedgerReconGrpcClient.LedgerTransactionDetail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core reconciliation engine that applies four invariant checks against each unmatched
 * bank statement line and transitions its status accordingly.
 *
 * <p>Invariants (all must pass for MATCHED):
 * <ol>
 *   <li>Ledger transaction exists for the given {@code end_to_end_id}.</li>
 *   <li>Statement amount (in pence) equals ledger amount to the exact penny.</li>
 *   <li>Statement currency equals ledger currency.</li>
 *   <li>Entry direction matches (statement DEBIT == ledger DEBIT).</li>
 * </ol>
 *
 * <p>Any invariant failure → {@code DISCREPANCY}. Missing ledger entry → {@code DISCREPANCY}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEngine {

    private final LedgerReconGrpcClient ledgerClient;
    private final BankStatementLineRepository lineRepository;

    /**
     * Reconciles all UNMATCHED lines for the given statement ID.
     *
     * @param statementId the UUID of the bank statement to reconcile
     * @return summary counts of matched and discrepancy lines
     */
    public ReconciliationSummary reconcileStatement(UUID statementId) {
        List<BankStatementLineEntity> unmatchedLines =
                lineRepository.findByStatementIdAndReconciliationStatus(
                        statementId, ReconciliationStatus.UNMATCHED);

        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger discrepancy = new AtomicInteger(0);

        for (BankStatementLineEntity line : unmatchedLines) {
            processLine(line, matched, discrepancy);
        }

        log.info("Reconciliation complete for statement {}: {} matched, {} discrepancies out of {} lines",
                statementId, matched.get(), discrepancy.get(), unmatchedLines.size());

        return new ReconciliationSummary(unmatchedLines.size(), matched.get(), discrepancy.get());
    }

    private void processLine(BankStatementLineEntity line,
                             AtomicInteger matched,
                             AtomicInteger discrepancy) {
        String endToEndId = line.getEndToEndId();

        Optional<LedgerTransactionDetail> ledgerOptional = ledgerClient.findByEndToEndId(endToEndId);

        if (ledgerOptional.isEmpty()) {
            // Invariant 1 failed: no matching ledger transaction
            log.warn("DISCREPANCY — no ledger transaction for endToEndId '{}' (statementLine {})",
                    endToEndId, line.getId());
            markDiscrepancy(line, "No matching ledger transaction found");
            discrepancy.incrementAndGet();
            return;
        }

        LedgerTransactionDetail ledger = ledgerOptional.get();
        String discrepancyReason = checkInvariants(line, ledger);

        if (discrepancyReason == null) {
            markMatched(line, UUID.fromString(ledger.transactionId()));
            matched.incrementAndGet();
        } else {
            log.warn("DISCREPANCY — endToEndId '{}': {}", endToEndId, discrepancyReason);
            markDiscrepancy(line, discrepancyReason);
            discrepancy.incrementAndGet();
        }
    }

    /**
     * Returns null if all invariants pass, or a human-readable reason if any fail.
     * Invariants 2, 3, and 4 are checked.
     */
    private String checkInvariants(BankStatementLineEntity line, LedgerTransactionDetail ledger) {
        // Invariant 2: exact penny match
        if (line.getAmountInPence() != ledger.amount().toMinorUnits()) {
            return String.format(
                    "Amount mismatch: statement=%dp, ledger=%dp (variance=%dp)",
                    line.getAmountInPence(),
                    ledger.amount().toMinorUnits(),
                    line.getAmountInPence() - ledger.amount().toMinorUnits());
        }

        // Invariant 3: currency match
        if (!line.getCurrency().equalsIgnoreCase(ledger.currency())) {
            return String.format(
                    "Currency mismatch: statement=%s, ledger=%s",
                    line.getCurrency(), ledger.currency());
        }

        // Invariant 4: entry direction match
        if (line.getEntryType() != ledger.entryType()) {
            return String.format(
                    "Entry type mismatch: statement=%s, ledger=%s",
                    line.getEntryType(), ledger.entryType());
        }

        return null;
    }

    private void markMatched(BankStatementLineEntity line, UUID ledgerTransactionId) {
        line.setReconciliationStatus(ReconciliationStatus.MATCHED);
        line.setMatchedEntryId(ledgerTransactionId);
        lineRepository.save(line);
    }

    private void markDiscrepancy(BankStatementLineEntity line, String reason) {
        line.setReconciliationStatus(ReconciliationStatus.DISCREPANCY);
        lineRepository.save(line);
        // reason is logged by the caller; a future extension can persist it as an audit note
    }
}
