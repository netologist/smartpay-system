package com.hozgan.smartpay.ledger.web;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.ledger.TestcontainersConfiguration;
import com.hozgan.smartpay.ledger.dto.TransferRequest;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer integration test for {@link LedgerTransferController}.
 *
 * <p>Spring Boot 4.1 removed {@code @WebMvcTest}. We use {@code @SpringBootTest} with
 * {@code WebEnvironment.MOCK} and manually build MockMvc from the {@link WebApplicationContext}.
 * {@link AccountBalanceService} is replaced with a {@code @MockitoBean} for fast, isolated testing.
 *
 * <p>Verifies HTTP contract: status codes, response JSON shape, RFC 7807 error bodies.
 */
import org.junit.jupiter.api.Tag;

@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@DisplayName("LedgerTransferController — MockMvc Tests")
class LedgerTransferControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private AccountBalanceService accountBalanceService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TARGET_ID = UUID.randomUUID().toString();
    private static final String IDEMP_KEY  = "test-idemp-key-001";

    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/ledger/transfers — Happy Path")
    class TransferHappyPath {

        @Test
        @DisplayName("200 OK with correct JSON fields for a valid transfer request")
        void validTransferReturns200() throws Exception {
            TransferRequest req = new TransferRequest(
                    SOURCE_ID, TARGET_ID, 25_000L, "GBP",
                    "INVOICE_SETTLEMENT", "INV-001", "Settlement transfer");

            UUID txId = UUID.randomUUID();
            TransferResult mockResult = new TransferResult(
                    txId,
                    JournalStatus.POSTED,
                    Instant.now(),
                    Money.ofGBP("750.00"),
                    Money.ofGBP("250.00"),
                    Money.ofGBP("250.00"));

            when(accountBalanceService.transfer(
                    eq(UUID.fromString(SOURCE_ID)),
                    eq(UUID.fromString(TARGET_ID)),
                    any(Money.class),
                    eq("INVOICE_SETTLEMENT"),
                    eq("INV-001"),
                    eq(IDEMP_KEY),
                    any()))
                    .thenReturn(mockResult);

            mockMvc().perform(post("/api/v1/ledger/transfers")
                            .header("Idempotency-Key", IDEMP_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionId").value(txId.toString()))
                    .andExpect(jsonPath("$.status").value("POSTED"))
                    .andExpect(jsonPath("$.amount.currency").value("GBP"))
                    .andExpect(jsonPath("$.amount.amount").value("250.00"))
                    .andExpect(jsonPath("$.amount.amountInPence").value(25000))
                    .andExpect(jsonPath("$.sourceAvailableBalance.amountInPence").value(75000))
                    .andExpect(jsonPath("$.targetAvailableBalance.amountInPence").value(25000));
        }
    }

    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/ledger/transfers — Error Paths")
    class TransferErrorPaths {

        @Test
        @DisplayName("422 Unprocessable Entity when InsufficientFundsException is thrown")
        void insufficientFundsReturns422() throws Exception {
            TransferRequest req = new TransferRequest(
                    SOURCE_ID, TARGET_ID, 200_000L, "GBP",
                    "INVOICE_SETTLEMENT", "INV-ERR-001", "Should fail");

            when(accountBalanceService.transfer(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new InsufficientFundsException(
                            AccountId.of(UUID.fromString(SOURCE_ID)),
                            Money.ofGBP("2000.00"),
                            Money.ofGBP("100.00")));

            mockMvc().perform(post("/api/v1/ledger/transfers")
                            .header("Idempotency-Key", IDEMP_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Insufficient Funds"))
                    .andExpect(jsonPath("$.errorCode").value("ERR_INSUFFICIENT_FUNDS"));
        }

        @Test
        @DisplayName("404 Not Found when AccountNotFoundException is thrown")
        void accountNotFoundReturns404() throws Exception {
            TransferRequest req = new TransferRequest(
                    SOURCE_ID, TARGET_ID, 10_000L, "GBP",
                    "INVOICE_SETTLEMENT", "INV-ERR-002", "Should fail");

            when(accountBalanceService.transfer(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new AccountNotFoundException(AccountId.of(UUID.fromString(SOURCE_ID))));

            mockMvc().perform(post("/api/v1/ledger/transfers")
                            .header("Idempotency-Key", IDEMP_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Account Not Found"))
                    .andExpect(jsonPath("$.errorCode").value("ERR_ACCOUNT_NOT_FOUND"));
        }

        @Test
        @DisplayName("400 Bad Request when request body fails bean validation (blank sourceAccountId)")
        void blankSourceAccountIdReturns400() throws Exception {
            TransferRequest req = new TransferRequest(
                    "", TARGET_ID, 10_000L, "GBP",
                    "INVOICE_SETTLEMENT", "INV-ERR-003", "Should fail validation");

            mockMvc().perform(post("/api/v1/ledger/transfers")
                            .header("Idempotency-Key", IDEMP_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation Error"));
        }

        @Test
        @DisplayName("400 Bad Request when amountInPence is zero (fails @Positive constraint)")
        void zeroAmountReturns400() throws Exception {
            TransferRequest req = new TransferRequest(
                    SOURCE_ID, TARGET_ID, 0L, "GBP",
                    "INVOICE_SETTLEMENT", "INV-ERR-004", "Zero amount");

            mockMvc().perform(post("/api/v1/ledger/transfers")
                            .header("Idempotency-Key", IDEMP_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}
