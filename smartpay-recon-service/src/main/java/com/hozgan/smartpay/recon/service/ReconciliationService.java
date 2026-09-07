package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.ReconciliationStatus;
import com.hozgan.smartpay.recon.dto.ParsedStatementDto;
import com.hozgan.smartpay.recon.dto.ParsedStatementDto.ParsedStatementLineDto;
import com.hozgan.smartpay.recon.dto.ReconciliationSummary;
import com.hozgan.smartpay.recon.dto.response.StatementLineResponse;
import com.hozgan.smartpay.recon.dto.response.StatementUploadResponse;
import com.hozgan.smartpay.recon.entity.BankStatementEntity;
import com.hozgan.smartpay.recon.entity.BankStatementLineEntity;
import com.hozgan.smartpay.recon.repository.BankStatementLineRepository;
import com.hozgan.smartpay.recon.repository.BankStatementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Application service that orchestrates the three-step statement ingestion pipeline:
 * <ol>
 *   <li>Parse the CAMT.053 XML file (XXE-hardened)</li>
 *   <li>Persist the statement header and lines (all initially UNMATCHED)</li>
 *   <li>Run the automated reconciliation engine</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final Camt053XmlStatementParser parser;
    private final ReconciliationEngine engine;
    private final BankStatementRepository statementRepository;
    private final BankStatementLineRepository lineRepository;

    /**
     * Ingests a CAMT.053 XML multipart file, persists header and lines, runs reconciliation,
     * and returns a summary response.
     *
     * @param bankName the bank name provided by the caller (e.g. "ClearBank")
     * @param file     the uploaded multipart XML file
     * @return upload response with reconciliation summary counts
     * @throws IllegalArgumentException if the file is unreadable or the XML is invalid/XXE-poisoned
     */
    @Transactional
    public StatementUploadResponse ingestAndReconcile(String bankName, MultipartFile file) {
        ParsedStatementDto parsed;
        try {
            parsed = parser.parseStatement(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded statement file: " + e.getMessage(), e);
        }

        // Override bank name from form field if provided (takes precedence over XML)
        String resolvedBankName = (bankName != null && !bankName.isBlank()) ? bankName : parsed.bankName();

        // Persist header
        BankStatementEntity statementEntity = new BankStatementEntity(
                parsed.statementReference(),
                resolvedBankName,
                parsed.accountNumber(),
                parsed.statementDate(),
                parsed.openingBalance(),
                parsed.closingBalance()
        );
        statementRepository.save(statementEntity);

        // Persist lines (all UNMATCHED initially)
        UUID statementId = statementEntity.getId();
        List<BankStatementLineEntity> lineEntities = parsed.lines().stream()
                .map(line -> new BankStatementLineEntity(
                        statementId,
                        parsed.statementReference(),
                        line.endToEndId(),
                        line.amount(),
                        line.entryType(),
                        line.bookingDate()))
                .toList();
        lineRepository.saveAll(lineEntities);

        log.info("Persisted statement '{}' ({} lines) for bank '{}'",
                parsed.statementReference(), lineEntities.size(), resolvedBankName);

        // Run reconciliation engine
        ReconciliationSummary summary = engine.reconcileStatement(statementId);

        return new StatementUploadResponse(
                statementId,
                parsed.statementReference(),
                resolvedBankName,
                parsed.accountNumber(),
                parsed.statementDate(),
                parsed.openingBalance().currency().getCurrencyCode(),
                parsed.openingBalance(),
                parsed.closingBalance(),
                summary.totalLines(),
                summary.matchedLines(),
                summary.discrepancyLines(),
                Instant.now()
        );
    }

    /**
     * Returns all statement lines for a given statement, mapped to response DTOs.
     *
     * @param statementId the UUID of the bank statement
     * @return list of all lines (any status)
     * @throws EntityNotFoundException if no statement with that ID exists
     */
    @Transactional(readOnly = true)
    public List<StatementLineResponse> getLines(UUID statementId) {
        if (!statementRepository.existsById(statementId)) {
            throw new EntityNotFoundException("Bank statement not found: " + statementId);
        }
        return lineRepository.findByStatementId(statementId).stream()
                .map(entity -> new StatementLineResponse(
                        entity.getId(),
                        entity.getStatementId(),
                        entity.getStatementReference(),
                        entity.getEndToEndId(),
                        Money.ofMinor(entity.getAmountInPence(), Currency.getInstance(entity.getCurrency())),
                        entity.getEntryType(),
                        entity.getBookingDate(),
                        entity.getReconciliationStatus(),
                        entity.getMatchedEntryId()))
                .toList();
    }
}
