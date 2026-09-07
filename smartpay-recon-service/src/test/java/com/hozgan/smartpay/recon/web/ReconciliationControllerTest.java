package com.hozgan.smartpay.recon.web;

import com.hozgan.smartpay.recon.ReconTestcontainersConfiguration;
import com.hozgan.smartpay.recon.dto.response.StatementUploadResponse;
import com.hozgan.smartpay.recon.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link ReconciliationController}.
 *
 * <p>Spring Boot test with MOCK web environment and mocked service layer.
 * Covers AC-1 (upload 201 response), AC-4 (XXE rejection 400), and the GET lines endpoint.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import({ReconTestcontainersConfiguration.class, ReconGrpcTestConfig.class})
class ReconciliationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReconciliationService reconciliationService;

    private MockMvc mockMvc;

    private static final String UPLOAD_URL = "/api/v1/recon/statements/upload";
    private static final String LINES_URL   = "/api/v1/recon/statements/{id}/lines";
    private static final UUID STATEMENT_ID  = UUID.fromString("0191c7e0-2210-7000-84a1-09aa12bc4410");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // -------------------------------------------------------------------------
    // AC-1: Valid CAMT.053 upload → HTTP 201 with summary
    // -------------------------------------------------------------------------

    @Test
    void uploadStatement_validFile_returns201WithSummary() throws Exception {
        StatementUploadResponse expectedResponse = new StatementUploadResponse(
                STATEMENT_ID,
                "STMT-CLEARBANK-2026-09-05-001",
                "ClearBank",
                "12345678",
                LocalDate.of(2026, 9, 5),
                "GBP",
                com.hozgan.smartpay.common.model.Money.of("100000.00", "GBP"),
                com.hozgan.smartpay.common.model.Money.of("145000.00", "GBP"),
                100,
                98,
                2,
                Instant.now()
        );

        when(reconciliationService.ingestAndReconcile(eq("ClearBank"), any()))
                .thenReturn(expectedResponse);

        MockMultipartFile xmlFile = new MockMultipartFile(
                "file",
                "CAMT053_20260905.xml",
                MediaType.APPLICATION_XML_VALUE,
                "<Document/>".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(xmlFile)
                        .param("bankName", "ClearBank"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statementReference").value("STMT-CLEARBANK-2026-09-05-001"))
                .andExpect(jsonPath("$.bankName").value("ClearBank"))
                .andExpect(jsonPath("$.totalLinesParsed").value(100))
                .andExpect(jsonPath("$.matchedLines").value(98))
                .andExpect(jsonPath("$.discrepancyLines").value(2));
    }

    // -------------------------------------------------------------------------
    // AC-4: XXE / malformed XML → HTTP 400
    // -------------------------------------------------------------------------

    @Test
    void uploadStatement_illegalArgumentFromParser_returns400() throws Exception {
        when(reconciliationService.ingestAndReconcile(any(), any()))
                .thenThrow(new IllegalArgumentException("XML document rejected: possible XXE payload"));

        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file",
                "evil.xml",
                MediaType.APPLICATION_XML_VALUE,
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><foo>&xxe;</foo>".getBytes()
        );

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(maliciousFile)
                        .param("bankName", "EvilBank"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_STATEMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("XXE")));
    }

    // -------------------------------------------------------------------------
    // GET lines → HTTP 200
    // -------------------------------------------------------------------------

    @Test
    void getStatementLines_existingStatement_returns200WithLines() throws Exception {
        when(reconciliationService.getLines(STATEMENT_ID)).thenReturn(List.of());

        mockMvc.perform(get(LINES_URL, STATEMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getStatementLines_notFound_returns404() throws Exception {
        when(reconciliationService.getLines(STATEMENT_ID))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Bank statement not found: " + STATEMENT_ID));

        mockMvc.perform(get(LINES_URL, STATEMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ERR_NOT_FOUND"));
    }
}
