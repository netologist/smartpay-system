package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.recon.dto.ParsedStatementDto;
import com.hozgan.smartpay.recon.dto.ParsedStatementDto.ParsedStatementLineDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Parses ISO-20022 CAMT.053 XML bank statements into in-memory {@link ParsedStatementDto} objects.
 *
 * <p><strong>Security:</strong> The XML parser is hardened against XML External Entity (XXE)
 * injection by disabling DOCTYPE declarations and external entity resolution before any
 * document is parsed (AC-4).
 *
 * <p>Supports camt.053.001.02 and camt.053.001.08 namespace variants by using
 * namespace-agnostic local-name element lookups.
 */
@Component
@Slf4j
public class Camt053XmlStatementParser {

    /**
     * Parses a CAMT.053 XML input stream into a {@link ParsedStatementDto}.
     *
     * @param xmlInputStream the raw XML bytes from the multipart upload
     * @return parsed statement header and all statement lines
     * @throws IllegalArgumentException if the XML is malformed, contains a DOCTYPE declaration,
     *                                  or any required element is missing
     */
    public ParsedStatementDto parseStatement(InputStream xmlInputStream) {
        Document doc = parseSecure(xmlInputStream);

        // --- Statement header (Stmt element) ---
        Element stmt = requireFirst(doc, "Stmt");

        String statementReference = textOf(stmt, "Id");
        LocalDate statementDate = LocalDate.parse(textOf(stmt, "CreDtTm").substring(0, 10));

        // Account identification
        Element acct = requireFirst(stmt, "Acct");
        String accountNumber = textOf(acct, "Id");

        // Balances (Bal elements: opening = OPBD, closing = CLBD)
        Money openingBalance = parseBalance(stmt, "OPBD");
        Money closingBalance = parseBalance(stmt, "CLBD");

        String currency = openingBalance.currency().getCurrencyCode();

        // Bank name from Group Header (GrpHdr > InstgAgt > FinInstnId > Nm)
        String bankName = parseBankName(doc);

        // --- Statement lines (Ntry elements) ---
        NodeList entries = stmt.getElementsByTagNameNS("*", "Ntry");
        List<ParsedStatementLineDto> lines = new ArrayList<>(entries.getLength());

        for (int i = 0; i < entries.getLength(); i++) {
            Element ntry = (Element) entries.item(i);
            lines.add(parseEntry(ntry, currency));
        }

        log.info("Parsed CAMT.053 statement '{}' with {} entries from bank '{}'",
                statementReference, lines.size(), bankName);

        return new ParsedStatementDto(
                statementReference,
                bankName,
                accountNumber,
                statementDate,
                openingBalance,
                closingBalance,
                lines
        );
    }

    // --- Private helpers ---

    private Document parseSecure(InputStream xmlInputStream) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            // XXE hardening — must be set before newDocumentBuilder() is called
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(xmlInputStream);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("XML parser configuration error", e);
        } catch (org.xml.sax.SAXException e) {
            // DTD declaration triggers org.xml.sax.SAXParseException — maps to 400
            throw new IllegalArgumentException(
                    "XML document rejected: possible XXE payload or malformed XML — " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to read XML input stream", e);
        }
    }

    private Money parseBalance(Element stmt, String cdType) {
        NodeList balNodes = stmt.getElementsByTagNameNS("*", "Bal");
        for (int i = 0; i < balNodes.getLength(); i++) {
            Element bal = (Element) balNodes.item(i);
            NodeList cdNodes = bal.getElementsByTagNameNS("*", "Cd");
            if (cdNodes.getLength() > 0 && cdType.equals(cdNodes.item(0).getTextContent().trim())) {
                Element amt = (Element) bal.getElementsByTagNameNS("*", "Amt").item(0);
                if (amt == null) {
                    throw new IllegalArgumentException("Missing Amt element for balance type " + cdType);
                }
                String currencyCode = amt.getAttribute("Ccy");
                if (currencyCode.isBlank()) {
                    // fallback: look for nested Ccy element
                    NodeList ccyNodes = bal.getElementsByTagNameNS("*", "Ccy");
                    currencyCode = ccyNodes.getLength() > 0 ? ccyNodes.item(0).getTextContent().trim() : "GBP";
                }
                BigDecimal amountDecimal = new BigDecimal(amt.getTextContent().trim());
                return Money.of(amountDecimal, Currency.getInstance(currencyCode));
            }
        }
        throw new IllegalArgumentException("CAMT.053 statement is missing balance type: " + cdType);
    }

    private ParsedStatementLineDto parseEntry(Element ntry, String statementCurrency) {
        // Amount with currency attribute: <Amt Ccy="GBP">975.00</Amt>
        Element amtEl = (Element) ntry.getElementsByTagNameNS("*", "Amt").item(0);
        if (amtEl == null) {
            throw new IllegalArgumentException("Entry is missing Amt element");
        }
        String ccy = amtEl.getAttribute("Ccy");
        if (ccy.isBlank()) ccy = statementCurrency;
        BigDecimal amount = new BigDecimal(amtEl.getTextContent().trim());
        Money money = Money.of(amount, Currency.getInstance(ccy));

        // Credit/Debit Indicator: CdtDbtInd — CRDT or DBIT
        String cdtDbtInd = textOf(ntry, "CdtDbtInd").trim();
        EntryType entryType = "CRDT".equalsIgnoreCase(cdtDbtInd) ? EntryType.CREDIT : EntryType.DEBIT;

        // Booking date
        LocalDate bookingDate = LocalDate.parse(textOf(ntry, "BookgDt").substring(0, 10));

        // End-to-end id: inside Ntry/NtryDtls/TxDtls/Refs/EndToEndId
        String endToEndId = findEndToEndId(ntry);

        return new ParsedStatementLineDto(endToEndId, money, entryType, bookingDate);
    }

    private String findEndToEndId(Element ntry) {
        // Depth-first search for EndToEndId element within the entry
        NodeList candidates = ntry.getElementsByTagNameNS("*", "EndToEndId");
        if (candidates.getLength() > 0) {
            return candidates.item(0).getTextContent().trim();
        }
        // Fallback to transaction id (InstrId) if EndToEndId is absent
        NodeList instrId = ntry.getElementsByTagNameNS("*", "InstrId");
        if (instrId.getLength() > 0) {
            return instrId.item(0).getTextContent().trim();
        }
        throw new IllegalArgumentException("Statement entry is missing EndToEndId and InstrId");
    }

    private String parseBankName(Document doc) {
        try {
            NodeList nmNodes = doc.getElementsByTagNameNS("*", "Nm");
            if (nmNodes.getLength() > 0) {
                return nmNodes.item(0).getTextContent().trim();
            }
        } catch (Exception e) {
            log.debug("Could not extract bank name from GrpHdr, defaulting to 'Unknown'");
        }
        return "Unknown";
    }

    private Element requireFirst(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("CAMT.053 XML is missing required element: <" + localName + ">");
        }
        return (Element) nodes.item(0);
    }

    private Element requireFirst(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException("CAMT.053 XML is missing required element: <" + localName + ">");
        }
        return (Element) nodes.item(0);
    }

    private String textOf(Element parent, String localName) {
        return requireFirst(parent, localName).getTextContent().trim();
    }

    private String textOf(Document doc, String localName) {
        return requireFirst(doc, localName).getTextContent().trim();
    }
}
