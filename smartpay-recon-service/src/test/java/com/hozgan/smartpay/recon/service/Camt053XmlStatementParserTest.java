package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.recon.dto.ParsedStatementDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Camt053XmlStatementParser}.
 *
 * <p>Tests run entirely in-memory — no Spring context, no Docker.
 */
@Tag("unit")
class Camt053XmlStatementParserTest {

    private final Camt053XmlStatementParser parser = new Camt053XmlStatementParser();

    // -------------------------------------------------------------------------
    // AC-1: Valid CAMT.053 parses header and lines correctly
    // -------------------------------------------------------------------------

    @Test
    void parseStatement_validCamt053_parsesHeaderCorrectly() {
        ParsedStatementDto result = parseFixture("fixtures/camt053_sample.xml");

        assertThat(result.statementReference()).isEqualTo("STMT-CLEARBANK-2026-09-05-001");
        assertThat(result.bankName()).isEqualTo("ClearBank");
        assertThat(result.accountNumber()).isEqualTo("12345678");
        assertThat(result.statementDate().toString()).isEqualTo("2026-09-05");
        assertThat(result.openingBalance().toMinorUnits()).isEqualTo(10_000_000L);
        assertThat(result.closingBalance().toMinorUnits()).isEqualTo(14_500_000L);
        assertThat(result.openingBalance().currency().getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    void parseStatement_validCamt053_parsesAllLines() {
        ParsedStatementDto result = parseFixture("fixtures/camt053_sample.xml");

        assertThat(result.lines()).hasSize(2);
    }

    @Test
    void parseStatement_validCamt053_firstLineIsDebitEntry() {
        ParsedStatementDto result = parseFixture("fixtures/camt053_sample.xml");

        ParsedStatementDto.ParsedStatementLineDto line = result.lines().get(0);
        assertThat(line.endToEndId()).isEqualTo("E2E-FACT-0841");
        assertThat(line.amount().toMinorUnits()).isEqualTo(97_500L);
        assertThat(line.entryType()).isEqualTo(EntryType.DEBIT);
        assertThat(line.bookingDate().toString()).isEqualTo("2026-09-05");
    }

    @Test
    void parseStatement_validCamt053_secondLineIsCreditEntry() {
        ParsedStatementDto result = parseFixture("fixtures/camt053_sample.xml");

        ParsedStatementDto.ParsedStatementLineDto line = result.lines().get(1);
        assertThat(line.endToEndId()).isEqualTo("E2E-DEPOSIT-001");
        assertThat(line.amount().toMinorUnits()).isEqualTo(5_000_000L);
        assertThat(line.entryType()).isEqualTo(EntryType.CREDIT);
    }

    // -------------------------------------------------------------------------
    // AC-4: XXE attack payload is rejected before any network call
    // -------------------------------------------------------------------------

    @Test
    void parseStatement_xxePayload_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parseFixture("fixtures/camt053_xxe_payload.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XML document rejected");
    }

    @Test
    void parseStatement_emptyStream_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> parser.parseStatement(InputStream.nullInputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ParsedStatementDto parseFixture(String classpathResource) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        assertThat(is).as("Test fixture '%s' not found on classpath", classpathResource).isNotNull();
        return parser.parseStatement(is);
    }
}
