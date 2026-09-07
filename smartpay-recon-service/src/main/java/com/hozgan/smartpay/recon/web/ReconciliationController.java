package com.hozgan.smartpay.recon.web;

import com.hozgan.smartpay.recon.dto.response.StatementLineResponse;
import com.hozgan.smartpay.recon.dto.response.StatementUploadResponse;
import com.hozgan.smartpay.recon.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for bank statement ingestion and reconciliation queries.
 *
 * <ul>
 *   <li>{@code POST /api/v1/recon/statements/upload} — Upload and auto-reconcile a CAMT.053 statement</li>
 *   <li>{@code GET  /api/v1/recon/statements/{id}/lines} — Query all lines for a statement</li>
 * </ul>
 *
 * Access is restricted to callers with {@code ROLE_FINANCE_OPS} (enforced at the API gateway layer).
 */
@RestController
@RequestMapping("/api/v1/recon/statements")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * Ingests a CAMT.053 XML bank statement, persists all lines as UNMATCHED,
     * runs the automated reconciliation engine, and returns the outcome summary.
     *
     * @param bankName the name of the clearing bank (e.g. "ClearBank")
     * @param file     multipart XML file; max 20 MB (enforced in application.yml)
     * @return HTTP 201 Created with {@link StatementUploadResponse}
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementUploadResponse> uploadStatement(
            @RequestParam("bankName") String bankName,
            @RequestParam("file") MultipartFile file) {

        log.info("Received bank statement upload request: bankName='{}', filename='{}'",
                bankName, file.getOriginalFilename());

        StatementUploadResponse response = reconciliationService.ingestAndReconcile(bankName, file);

        log.info("Statement '{}' ingested: {} matched, {} discrepancies",
                response.statementReference(), response.matchedLines(), response.discrepancyLines());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns all bank statement lines for a given statement ID.
     *
     * @param id the UUID of the bank statement
     * @return HTTP 200 OK with list of {@link StatementLineResponse}
     */
    @GetMapping("/{id}/lines")
    public ResponseEntity<List<StatementLineResponse>> getStatementLines(@PathVariable UUID id) {
        List<StatementLineResponse> lines = reconciliationService.getLines(id);
        return ResponseEntity.ok(lines);
    }
}
