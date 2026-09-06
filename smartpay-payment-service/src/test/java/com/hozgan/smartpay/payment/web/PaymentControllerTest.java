package com.hozgan.smartpay.payment.web;

import tools.jackson.databind.ObjectMapper;
import com.hozgan.smartpay.common.exception.IdempotencyConflictException;
import com.hozgan.smartpay.common.exception.RequestHashMismatchException;
import com.hozgan.smartpay.common.model.id.IdempotencyKey;
import com.hozgan.smartpay.common.model.id.TenantId;
import com.hozgan.smartpay.payment.TestcontainersConfiguration;
import com.hozgan.smartpay.payment.dto.request.PaymentRequest;
import com.hozgan.smartpay.payment.dto.response.PaymentResponse;
import com.hozgan.smartpay.payment.service.PaymentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class PaymentControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }
    private static final String INITIATE_URL = "/api/v1/payments/initiate";
    private static final String IDEMPOTENCY_KEY = "PAY-KEY-001";

    @Test
    void initiatePayment_validRequest_returns201() throws Exception {
        PaymentRequest request = buildRequest();
        PaymentResponse mockResponse = new PaymentResponse(
                "0191c7c4-8891-7000-84a1-00aa4912fa99",
                "INITIATED",
                97500L,
                "GBP",
                "975.00",
                "E2E-SMARTPAY-20260905-9912",
                "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
                "0191c7a2-9b24-7f11-9a1c-8e9942a0b124",
                Instant.now()
        );
        when(paymentService.initiatePayment(any(), any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post(INITIATE_URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value("0191c7c4-8891-7000-84a1-00aa4912fa99"))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.amountInPence").value(97500))
                .andExpect(jsonPath("$.endToEndId").value("E2E-SMARTPAY-20260905-9912"));
    }

    @Test
    void initiatePayment_idempotencyConflict_returns409() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any()))
                .thenThrow(new IdempotencyConflictException(
                        IdempotencyKey.of(IDEMPOTENCY_KEY), "PROCESSING"));

        mockMvc.perform(post(INITIATE_URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buildRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void initiatePayment_hashMismatch_returns422() throws Exception {
        when(paymentService.initiatePayment(any(), any(), any(), any()))
                .thenThrow(new RequestHashMismatchException(IdempotencyKey.of(IDEMPOTENCY_KEY)));

        mockMvc.perform(post(INITIATE_URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buildRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_REQUEST_HASH_MISMATCH"));
    }

    @Test
    void initiatePayment_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post(INITIATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(buildRequest())))
                .andExpect(status().isBadRequest());
    }

    private PaymentRequest buildRequest() {
        return new PaymentRequest(
                "TENANT-UK-01",
                UUID.fromString("0191c7a2-9b24-7f11-9a1c-3d842b10a512"),
                UUID.fromString("0191c7a2-9b24-7f11-9a1c-8e9942a0b124"),
                97500L,
                "GBP",
                "FASTER_PAYMENTS",
                "PAYOUT-INV-0841",
                "20-00-00",
                "12345678"
        );
    }
}
