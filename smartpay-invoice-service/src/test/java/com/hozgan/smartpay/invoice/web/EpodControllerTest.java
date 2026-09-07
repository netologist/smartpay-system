package com.hozgan.smartpay.invoice.web;

import com.hozgan.smartpay.common.exception.DuplicateLoadException;
import com.hozgan.smartpay.common.exception.InvalidEpodSignatureException;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.invoice.TestcontainersConfiguration;
import com.hozgan.smartpay.invoice.dto.request.VerifyEpodRequest;
import com.hozgan.smartpay.invoice.entity.EpodRecordEntity;
import com.hozgan.smartpay.invoice.service.EpodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
@DisplayName("EpodController — MockMvc Tests")
class EpodControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private EpodService epodService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private static final String VALID_SIGNATURE = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("AC-1: Valid ePOD verification returns HTTP 201 Created with EpodRecordResponse")
    void ac1_validEpodReturnsCreated() throws Exception {
        CarrierId carrierId = CarrierId.generate();
        Instant deliveredAt = Instant.parse("2026-09-05T14:45:10Z");
        LoadId loadId = LoadId.of("LOAD-2026-UK-0841");

        VerifyEpodRequest request = new VerifyEpodRequest(
                loadId,
                carrierId,
                deliveredAt,
                new BigDecimal("51.5074"),
                new BigDecimal("-0.1278"),
                "https://s3.eu-west-2.amazonaws.com/smartpay-epod-production/loads/LOAD-0841.jpg",
                VALID_SIGNATURE
        );

        EpodRecordEntity entity = new EpodRecordEntity(
                loadId,
                carrierId,
                deliveredAt,
                GeoLocation.of(51.5074, -0.1278),
                request.photoS3Url(),
                VALID_SIGNATURE,
                true
        );

        when(epodService.verifyAndRecordEpod(
                eq(loadId), eq(carrierId), eq(deliveredAt),
                eq(request.latitude()), eq(request.longitude()),
                eq(request.photoS3Url()), eq(VALID_SIGNATURE)
        )).thenReturn(entity);

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value("LOAD-2026-UK-0841"))
                .andExpect(jsonPath("$.carrierId").value(carrierId.asString()))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    @DisplayName("AC-1: Invalid signature format throws InvalidEpodSignatureException -> HTTP 400 Bad Request")
    void ac1_invalidSignatureReturnsBadRequest() throws Exception {
        CarrierId carrierId = CarrierId.generate();
        LoadId loadId = LoadId.of("LOAD-2026-UK-0841");

        VerifyEpodRequest request = new VerifyEpodRequest(
                loadId,
                carrierId,
                Instant.now(),
                new BigDecimal("51.5074"),
                new BigDecimal("-0.1278"),
                "https://s3.eu-west-2.amazonaws.com/smartpay-epod-production/loads/LOAD-0841.jpg",
                "invalid-sha256-hash"
        );

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("AC-1: Service throws InvalidEpodSignatureException -> HTTP 400 with ERR_INVALID_EPOD_SIGNATURE")
    void ac1_serviceThrowsInvalidSignatureReturnsBadRequest() throws Exception {
        CarrierId carrierId = CarrierId.generate();
        LoadId loadId = LoadId.of("LOAD-2026-UK-0841");

        VerifyEpodRequest request = new VerifyEpodRequest(
                loadId,
                carrierId,
                Instant.now(),
                new BigDecimal("51.5074"),
                new BigDecimal("-0.1278"),
                "https://s3.eu-west-2.amazonaws.com/smartpay-epod-production/loads/LOAD-0841.jpg",
                VALID_SIGNATURE
        );

        when(epodService.verifyAndRecordEpod(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InvalidEpodSignatureException(loadId));

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_EPOD_SIGNATURE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("signature verification failed")));
    }

    @Test
    @DisplayName("AC-4: Duplicate ePOD submission throws DuplicateLoadException -> HTTP 409 Conflict")
    void ac4_duplicateLoadReturnsConflict() throws Exception {
        CarrierId carrierId = CarrierId.generate();
        LoadId loadId = LoadId.of("LOAD-2026-UK-0841");

        VerifyEpodRequest request = new VerifyEpodRequest(
                loadId,
                carrierId,
                Instant.now(),
                new BigDecimal("51.5074"),
                new BigDecimal("-0.1278"),
                "https://s3.eu-west-2.amazonaws.com/smartpay-epod-production/loads/LOAD-0841.jpg",
                VALID_SIGNATURE
        );

        when(epodService.verifyAndRecordEpod(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateLoadException(loadId));

        mockMvc.perform(post("/api/v1/epod/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ERR_DUPLICATE_LOAD"));
    }

    @Test
    @DisplayName("GET /api/v1/epod/{loadId} returns ePOD when found, 404 when absent")
    void getEpodByLoadId() throws Exception {
        LoadId loadId = LoadId.of("LOAD-FOUND");
        EpodRecordEntity entity = new EpodRecordEntity(
                loadId,
                CarrierId.generate(),
                Instant.now(),
                GeoLocation.of(51.5, -0.1),
                "https://s3/test.jpg",
                VALID_SIGNATURE,
                true
        );

        when(epodService.findByLoadId(eq(LoadId.of("LOAD-FOUND")))).thenReturn(Optional.of(entity));
        when(epodService.findByLoadId(eq(LoadId.of("LOAD-MISSING")))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/epod/LOAD-FOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadId").value("LOAD-FOUND"))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(get("/api/v1/epod/LOAD-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ERR_ENTITY_NOT_FOUND"));
    }
}
