package com.hozgan.smartpay.invoice.web;

import com.hozgan.smartpay.common.exception.DuplicateLoadException;
import com.hozgan.smartpay.common.exception.EntityNotFoundException;
import com.hozgan.smartpay.common.exception.InvoiceAlreadySettledException;
import com.hozgan.smartpay.common.model.InvoicePricing;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.TestcontainersConfiguration;
import com.hozgan.smartpay.invoice.dto.request.CreateInvoiceRequest;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import com.hozgan.smartpay.invoice.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@DisplayName("InvoiceController — MockMvc Tests")
class InvoiceControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private InvoiceService invoiceService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private InvoicePricing createSamplePricing() {
        Money base = Money.ofGBP("525.00");
        Money fuel = Money.ofGBP("63.00");
        Money vat = Money.ofGBP("117.60");
        return InvoicePricing.calculate(base, fuel, vat);
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("AC-2: POST /api/v1/invoices creates invoice and returns HTTP 201 with Money pricing")
    void ac2_createInvoiceReturnsCreated() throws Exception {
        UUID shipperId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        String loadId = "LOAD-2026-UK-0841";

        CreateInvoiceRequest request = new CreateInvoiceRequest(
                loadId,
                shipperId,
                carrierId,
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                "GBP"
        );

        InvoiceEntity entity = new InvoiceEntity(
                loadId,
                shipperId,
                carrierId,
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                createSamplePricing(),
                InvoiceStatus.EPOD_VERIFIED
        );

        when(invoiceService.createInvoice(
                eq(loadId), eq(shipperId), eq(carrierId),
                eq(VehicleType.ARTIC), eq(new BigDecimal("150.00")), eq("GBP")
        )).thenReturn(entity);

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value(loadId))
                .andExpect(jsonPath("$.vehicleType").value("ARTIC"))
                .andExpect(jsonPath("$.status").value("EPOD_VERIFIED"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                // Rich Money representation in DTO
                .andExpect(jsonPath("$.pricing.baseAmount.amount").value("525.00"))
                .andExpect(jsonPath("$.pricing.baseAmount.currency").value("GBP"))
                .andExpect(jsonPath("$.pricing.baseAmount.minorUnits").value(52500))
                .andExpect(jsonPath("$.pricing.baseAmountPence").value(52500))
                .andExpect(jsonPath("$.pricing.fuelSurcharge.amount").value("63.00"))
                .andExpect(jsonPath("$.pricing.fuelSurchargePence").value(6300))
                .andExpect(jsonPath("$.pricing.vatAmount.amount").value("117.60"))
                .andExpect(jsonPath("$.pricing.vatAmountPence").value(11760))
                .andExpect(jsonPath("$.pricing.totalAmount.amount").value("705.60"))
                .andExpect(jsonPath("$.pricing.totalAmount.minorUnits").value(70560))
                .andExpect(jsonPath("$.pricing.totalAmountPence").value(70560));
    }

    @Test
    @DisplayName("AC-4: Duplicate invoice creation throws DuplicateLoadException -> HTTP 409 Conflict")
    void ac4_duplicateInvoiceReturnsConflict() throws Exception {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "LOAD-DUP",
                UUID.randomUUID(),
                UUID.randomUUID(),
                VehicleType.VAN,
                new BigDecimal("50.00"),
                "GBP"
        );

        when(invoiceService.createInvoice(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateLoadException(new LoadId("LOAD-DUP")));

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_LOAD"));
    }

    @Test
    @DisplayName("GET /api/v1/invoices/{id} returns invoice when found, 404 when absent")
    void getInvoiceById() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        InvoiceEntity entity = new InvoiceEntity(
                "LOAD-TEST",
                UUID.randomUUID(),
                UUID.randomUUID(),
                VehicleType.LUTON,
                new BigDecimal("80.00"),
                createSamplePricing(),
                InvoiceStatus.EPOD_VERIFIED
        );
        entity.setId(invoiceId);

        when(invoiceService.getInvoiceById(invoiceId)).thenReturn(entity);
        when(invoiceService.getInvoiceById(eq(UUID.fromString("00000000-0000-0000-0000-000000000000"))))
                .thenThrow(new EntityNotFoundException("Invoice", "00000000-0000-0000-0000-000000000000"));

        mockMvc.perform(get("/api/v1/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.loadId").value("LOAD-TEST"));

        mockMvc.perform(get("/api/v1/invoices/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ERR_ENTITY_NOT_FOUND"));
    }

    @Test
    @DisplayName("AC-5: PUT /api/v1/invoices/{id}/cancel on SETTLED invoice -> HTTP 422 Unprocessable Entity")
    void ac5_cancelSettledInvoiceReturnsUnprocessableEntity() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        when(invoiceService.cancelInvoice(invoiceId))
                .thenThrow(new InvoiceAlreadySettledException(new InvoiceId(invoiceId)));

        mockMvc.perform(put("/api/v1/invoices/" + invoiceId + "/cancel"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVOICE_ALREADY_SETTLED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has already been settled")));
    }
}
