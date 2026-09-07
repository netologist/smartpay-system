package com.hozgan.smartpay.recon.dto;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;

import java.time.LocalDate;
import java.util.List;

/**
 * Parsed, in-memory representation of a CAMT.053 bank statement.
 * Holds header data and the list of parsed statement lines.
 * This is a transient domain object — never persisted directly.
 */
public record ParsedStatementDto(
        String statementReference,
        String bankName,
        String accountNumber,
        LocalDate statementDate,
        Money openingBalance,
        Money closingBalance,
        List<ParsedStatementLineDto> lines
) {

    /**
     * A single CAMT.053 statement line (Ntry element).
     */
    public record ParsedStatementLineDto(
            String endToEndId,
            Money amount,
            EntryType entryType,
            LocalDate bookingDate
    ) {}
}
