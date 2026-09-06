package com.hozgan.smartpay.payout.web;

import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome.FactoringStatus;
import com.hozgan.smartpay.payout.service.FactoringPayoutWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("FactoringWorkerController — Web API Unit Tests")
class FactoringWorkerControllerTest {

    @Mock
    private FactoringPayoutWorker payoutWorker;

    private FactoringWorkerController controller;

    @BeforeEach
    void setUp() {
        controller = new FactoringWorkerController(payoutWorker);
    }

    @Test
    @DisplayName("GET /api/v1/payouts/status returns ACTIVE status with virtual thread capability")
    void shouldReturnWorkerStatus() {
        ResponseEntity<Map<String, Object>> response = controller.getWorkerStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("worker")).isEqualTo("smartpay-payout-worker");
        assertThat(response.getBody().get("virtualThreads")).isEqualTo(true);
    }

    @Test
    @DisplayName("POST /api/v1/payouts/process-epod executes manual factoring workflow")
    void shouldTriggerManualFactoringWorkflow() {
        LoadId loadId = LoadId.of("LOAD-API-001");
        CarrierId carrierId = CarrierId.generate();
        InvoiceId invoiceId = InvoiceId.generate();

        EpodVerifiedEvent event = EpodVerifiedEvent.of(
                loadId, carrierId, Instant.now(), GeoLocation.of(51.5, -0.1)
        );

        when(payoutWorker.processDeliveryVerification(any(EpodVerifiedEvent.class)))
                .thenReturn(FactoringPayoutOutcome.approved(
                        invoiceId, carrierId,
                        Money.of("1000.00", Money.GBP),
                        Money.of("25.00", Money.GBP),
                        Money.of("975.00", Money.GBP),
                        "PAY-API-100"
                ));

        ResponseEntity<FactoringPayoutOutcome> response = controller.processEpodManually(event);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(FactoringStatus.APPROVED);
        assertThat(response.getBody().paymentId()).isEqualTo("PAY-API-100");

        verify(payoutWorker).processDeliveryVerification(event);
    }
}
