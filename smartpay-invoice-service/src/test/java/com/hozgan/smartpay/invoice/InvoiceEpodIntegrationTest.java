package com.hozgan.smartpay.invoice;

import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.enums.VehicleType;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.invoice.dto.request.CreateInvoiceRequest;
import com.hozgan.smartpay.invoice.dto.request.VerifyEpodRequest;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import com.hozgan.smartpay.invoice.entity.InvoiceEntity;
import com.hozgan.smartpay.invoice.repository.EpodRecordRepository;
import com.hozgan.smartpay.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@Transactional
class InvoiceEpodIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private EpodRecordRepository epodRecordRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String VALID_SIGNATURE = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("AC-1: Electronic Delivery Proof (ePOD) Verification persisted in PostgreSQL")
    void ac1_epodVerificationEndToEnd() throws Exception {
        LoadId loadId = LoadId.of("LOAD-IT-AC1-" + UUID.randomUUID().toString().substring(0, 8));
        CarrierId carrierId = CarrierId.generate();
        Instant deliveredAt = Instant.parse("2026-09-05T14:45:10Z");

        VerifyEpodRequest request = new VerifyEpodRequest(
                loadId,
                carrierId,
                deliveredAt,
                new BigDecimal("51.5074000"),
                new BigDecimal("-0.1278000"),
                "https://s3.eu-west-2.amazonaws.com/smartpay-epod/loads/load-0841.jpg",
                VALID_SIGNATURE
        );

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value(loadId.asString()))
                .andExpect(jsonPath("$.verified").value(true));

        // Verify persistence in PostgreSQL
        Optional<EpodRecordEntity> persisted = epodRecordRepository.findByLoadId(loadId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().isVerified()).isTrue();
        assertThat(persisted.get().getCarrierId()).isEqualTo(carrierId);
        assertThat(persisted.get().getSignatureHash()).isEqualTo(VALID_SIGNATURE);
        assertThat(persisted.get().getLatitude()).isEqualByComparingTo("51.5074000");
        assertThat(persisted.get().getLongitude()).isEqualByComparingTo("-0.1278000");
    }

    @Test
    @DisplayName("AC-2: Freight Invoice Itemized Pricing Calculation and Database Persistence")
    void ac2_freightInvoicePricingAndPersistence() throws Exception {
        LoadId loadId = LoadId.of("LOAD-IT-AC2-" + UUID.randomUUID().toString().substring(0, 8));
        ShipperId shipperId = ShipperId.generate();
        CarrierId carrierId = CarrierId.generate();

        CreateInvoiceRequest request = new CreateInvoiceRequest(
                loadId,
                shipperId,
                carrierId,
                VehicleType.ARTIC,
                new BigDecimal("150.00"),
                "GBP"
        );

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value(loadId.asString()))
                .andExpect(jsonPath("$.vehicleType").value("ARTIC"))
                .andExpect(jsonPath("$.status").value("EPOD_VERIFIED"))
                .andExpect(jsonPath("$.pricing.baseAmount.amount").value("525.00"))
                .andExpect(jsonPath("$.pricing.baseAmountPence").value(52500))
                .andExpect(jsonPath("$.pricing.fuelSurcharge.amount").value("63.00"))
                .andExpect(jsonPath("$.pricing.fuelSurchargePence").value(6300))
                .andExpect(jsonPath("$.pricing.vatAmount.amount").value("117.60"))
                .andExpect(jsonPath("$.pricing.vatAmountPence").value(11760))
                .andExpect(jsonPath("$.pricing.totalAmount.amount").value("705.60"))
                .andExpect(jsonPath("$.pricing.totalAmountPence").value(70560));

        // Verify database persistence in PostgreSQL
        Optional<InvoiceEntity> persisted = invoiceRepository.findByLoadId(loadId);
        assertThat(persisted).isPresent();
        InvoiceEntity invoice = persisted.get();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.EPOD_VERIFIED);
        assertThat(invoice.getBaseAmountPence()).isEqualTo(52500L);
        assertThat(invoice.getFuelSurchargePence()).isEqualTo(6300L);
        assertThat(invoice.getVatAmountPence()).isEqualTo(11760L);
        assertThat(invoice.getTotalAmountPence()).isEqualTo(70560L);
        assertThat(invoice.getCurrency()).isEqualTo("GBP");
        assertThat(invoice.getVehicleType()).isEqualTo(VehicleType.ARTIC);
    }

    @Test
    @DisplayName("AC-3: Multi-Currency Invoice Support (EUR)")
    void ac3_multiCurrencyInvoiceEUR() throws Exception {
        LoadId loadId = LoadId.of("LOAD-IT-AC3-" + UUID.randomUUID().toString().substring(0, 8));
        ShipperId shipperId = ShipperId.generate();
        CarrierId carrierId = CarrierId.generate();

        CreateInvoiceRequest request = new CreateInvoiceRequest(
                loadId,
                shipperId,
                carrierId,
                VehicleType.ARTIC,
                new BigDecimal("100.00"),
                "EUR"
        );

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.pricing.baseAmount.amount").value("350.00"))
                .andExpect(jsonPath("$.pricing.baseAmountPence").value(35000))
                .andExpect(jsonPath("$.pricing.fuelSurcharge.amount").value("42.00"))
                .andExpect(jsonPath("$.pricing.fuelSurchargePence").value(4200))
                .andExpect(jsonPath("$.pricing.vatAmount.amount").value("78.40"))
                .andExpect(jsonPath("$.pricing.vatAmountPence").value(7840))
                .andExpect(jsonPath("$.pricing.totalAmount.amount").value("470.40"))
                .andExpect(jsonPath("$.pricing.totalAmountPence").value(47040));

        Optional<InvoiceEntity> persisted = invoiceRepository.findByLoadId(loadId);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getCurrency()).isEqualTo("EUR");
        assertThat(persisted.get().getTotalAmountPence()).isEqualTo(47040L);
    }

    @Test
    @DisplayName("AC-4: Duplicate ePOD and Invoice Creation Rejected with HTTP 409 Conflict")
    void ac4_duplicateDeliveryPrevention() throws Exception {
        LoadId loadId = LoadId.of("LOAD-IT-AC4-" + UUID.randomUUID().toString().substring(0, 8));
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();

        // 1. Submit initial ePOD
        VerifyEpodRequest epodRequest = new VerifyEpodRequest(
                loadId,
                carrierId,
                Instant.now(),
                new BigDecimal("51.5074"),
                new BigDecimal("-0.1278"),
                "https://s3/test.jpg",
                VALID_SIGNATURE
        );

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(epodRequest)))
                .andExpect(status().isCreated());

        // 2. Resubmit same ePOD -> HTTP 409 Conflict
        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(epodRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_LOAD"));

        // 3. Create initial invoice
        CreateInvoiceRequest invoiceRequest = new CreateInvoiceRequest(
                loadId,
                shipperId,
                carrierId,
                VehicleType.VAN,
                new BigDecimal("50.00"),
                "GBP"
        );

        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceRequest)))
                .andExpect(status().isCreated());

        // 4. Resubmit same invoice -> HTTP 409 Conflict
        mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_LOAD"));
    }

    @Test
    @DisplayName("AC-5: Settlement Mutability Lock prevents modification of SETTLED invoice")
    void ac5_settlementMutabilityLock() throws Exception {
        LoadId loadId = LoadId.of("LOAD-IT-AC5-" + UUID.randomUUID().toString().substring(0, 8));
        ShipperId shipperId = ShipperId.generate();
        CarrierId carrierId = CarrierId.generate();

        CreateInvoiceRequest invoiceRequest = new CreateInvoiceRequest(
                loadId,
                shipperId,
                carrierId,
                VehicleType.LUTON,
                new BigDecimal("75.00"),
                "GBP"
        );

        String responseJson = mockMvc.perform(post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invoiceRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String invoiceIdStr = objectMapper.readTree(responseJson).get("invoiceId").asText();
        UUID invoiceId = UUID.fromString(invoiceIdStr);

        // Transition invoice to SETTLED
        mockMvc.perform(put("/api/v1/invoices/" + invoiceId + "/status?status=SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // Attempt cancellation on SETTLED invoice -> HTTP 422
        mockMvc.perform(put("/api/v1/invoices/" + invoiceId + "/cancel"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVOICE_ALREADY_SETTLED"));

        // Attempt status update on SETTLED invoice -> HTTP 422
        mockMvc.perform(put("/api/v1/invoices/" + invoiceId + "/status?status=FACTORING_APPROVED"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVOICE_ALREADY_SETTLED"));
    }

    @Test
    @DisplayName("Database check constraint chk_vehicle_type allows all vehicle types (VAN, LUTON, 7_5T, ARTIC)")
    void allVehicleTypesPersistCleanly() throws Exception {
        VehicleType[] types = {VehicleType.VAN, VehicleType.LUTON, VehicleType.SEVEN_POINT_FIVE_TONNE, VehicleType.ARTIC};

        for (VehicleType vt : types) {
            LoadId loadId = LoadId.of("LOAD-VT-" + vt.name() + "-" + UUID.randomUUID().toString().substring(0, 8));
            CreateInvoiceRequest req = new CreateInvoiceRequest(
                    loadId,
                    ShipperId.generate(),
                    CarrierId.generate(),
                    vt,
                    new BigDecimal("100.00"),
                    "GBP"
            );

            mockMvc.perform(post("/api/v1/invoices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            Optional<InvoiceEntity> saved = invoiceRepository.findByLoadId(loadId);
            assertThat(saved).isPresent();
            assertThat(saved.get().getVehicleType()).isEqualTo(vt);
        }
    }
}
