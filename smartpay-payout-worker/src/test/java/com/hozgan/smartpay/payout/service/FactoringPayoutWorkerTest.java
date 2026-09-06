package com.hozgan.smartpay.payout.service;

import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
import com.hozgan.smartpay.common.model.GeoLocation;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.InvoiceStatus;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.common.model.id.LoadId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.payout.client.InvoiceClient;
import com.hozgan.smartpay.payout.client.PaymentGrpcClient;
import com.hozgan.smartpay.payout.client.PaymentGrpcClient.PaymentInitiationResult;
import com.hozgan.smartpay.payout.client.RiskGrpcClient;
import com.hozgan.smartpay.payout.client.RiskGrpcClient.RiskEvaluationResult;
import com.hozgan.smartpay.payout.client.dto.InvoiceDto;
import com.hozgan.smartpay.payout.client.dto.InvoiceDto.PricingDto;
import com.hozgan.smartpay.payout.config.PayoutWorkerProperties;
import com.hozgan.smartpay.payout.domain.FactoringCalculationResult;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome.FactoringStatus;
import com.hozgan.smartpay.proto.payment.PaymentStatusProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("FactoringPayoutWorker — Unit Tests")
class FactoringPayoutWorkerTest {

    @Mock
    private InvoiceClient invoiceClient;

    @Mock
    private RiskGrpcClient riskGrpcClient;

    @Mock
    private PaymentGrpcClient paymentGrpcClient;

    @Mock
    private FactoringEventPublisher eventPublisher;

    private FactoringCalculationEngine calculationEngine;
    private PayoutWorkerProperties properties;
    private FactoringPayoutWorker worker;

    private LoadId loadId;
    private CarrierId carrierId;
    private ShipperId shipperId;
    private InvoiceId invoiceId;
    private EpodVerifiedEvent epodEvent;

    @BeforeEach
    void setUp() {
        calculationEngine = new FactoringCalculationEngine();
        properties = new PayoutWorkerProperties(
                "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
                "TENANT-UK-01",
                new BigDecimal("2.5"),
                40,
                5000000L,
                "http://localhost:8081",
                "localhost",
                9094,
                "localhost",
                9092,
                null,
                null
        );

        worker = new FactoringPayoutWorker(
                invoiceClient,
                riskGrpcClient,
                paymentGrpcClient,
                calculationEngine,
                eventPublisher,
                properties
        );

        loadId = LoadId.of("LOAD-2026-001");
        carrierId = CarrierId.generate();
        shipperId = ShipperId.generate();
        invoiceId = InvoiceId.generate();

        epodEvent = EpodVerifiedEvent.of(
                loadId,
                carrierId,
                Instant.now(),
                GeoLocation.of(51.5074, -0.1278)
        );
    }

    private InvoiceDto createVerifiedInvoice(Money gross) {
        Money base = gross.minus(Money.of("50.00", gross.currency()));
        Money fuel = Money.of("20.00", gross.currency());
        Money vat = Money.of("30.00", gross.currency());
        PricingDto pricing = new PricingDto(base, fuel, vat, gross);

        return new InvoiceDto(
                invoiceId,
                loadId,
                shipperId,
                carrierId,
                "ARTICULATED_LORRY",
                new BigDecimal("150.00"),
                gross.currency().getCurrencyCode(),
                pricing,
                InvoiceStatus.EPOD_VERIFIED,
                Instant.now()
        );
    }

    @Test
    @DisplayName("AC-3: Factoring Disbursement via Payment gRPC for risk-approved invoice")
    void shouldSuccessfullyApproveAndDisburseFactoringAdvance() {
        // Given: £1000.00 gross invoice
        Money gross = Money.of("1000.00", Money.GBP);
        InvoiceDto invoice = createVerifiedInvoice(gross);

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(invoice));
        when(riskGrpcClient.evaluateCarrierRisk(carrierId, shipperId, gross))
                .thenReturn(new RiskEvaluationResult(true, 12, "LOW", "Clean credit history"));

        Money netPayout = Money.of("975.00", Money.GBP);
        when(paymentGrpcClient.initiateDisbursement(invoiceId, carrierId, netPayout))
                .thenReturn(new PaymentInitiationResult("PAY-9999", PaymentStatusProto.INITIATED, "E2E-123"));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.APPROVED);
        assertThat(outcome.invoiceId()).isEqualTo(invoiceId);
        assertThat(outcome.carrierId()).isEqualTo(carrierId);
        assertThat(outcome.grossAmount()).isEqualTo(gross);
        assertThat(outcome.factoringFee()).isEqualTo(Money.of("25.00", Money.GBP));
        assertThat(outcome.netPayoutAmount()).isEqualTo(netPayout);
        assertThat(outcome.paymentId()).isEqualTo("PAY-9999");

        // Verify status transition to FACTORING_APPROVED
        verify(invoiceClient).updateInvoiceStatus(invoiceId, InvoiceStatus.FACTORING_APPROVED);

        // Verify event publication
        verify(eventPublisher).publishApprovedPayout(eq(invoiceId), eq(carrierId), any(FactoringCalculationResult.class));
    }

    @Test
    @DisplayName("AC-2: Fraud Score Rejection when carrier risk score exceeds threshold (score = 75 > 40)")
    void shouldRejectFactoringAdvanceWhenRiskScoreExceedsThreshold() {
        // Given
        Money gross = Money.of("1000.00", Money.GBP);
        InvoiceDto invoice = createVerifiedInvoice(gross);

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(invoice));
        // Risk score 75 > threshold 40
        when(riskGrpcClient.evaluateCarrierRisk(carrierId, shipperId, gross))
                .thenReturn(new RiskEvaluationResult(true, 75, "CRITICAL", "High probability of default"));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.RISK_REJECTED);
        assertThat(outcome.reasoning()).contains("Risk score=75");

        // Verify no payment order is dispatched
        verify(paymentGrpcClient, never()).initiateDisbursement(any(), any(), any());
        verify(invoiceClient, never()).updateInvoiceStatus(any(), any());
        verify(eventPublisher, never()).publishApprovedPayout(any(), any(), any());
    }

    @Test
    @DisplayName("AC-2: Fraud Score Rejection when RiskService returns approved = false")
    void shouldRejectFactoringAdvanceWhenRiskServiceExplicitlyDeclines() {
        // Given
        Money gross = Money.of("1000.00", Money.GBP);
        InvoiceDto invoice = createVerifiedInvoice(gross);

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(invoice));
        when(riskGrpcClient.evaluateCarrierRisk(carrierId, shipperId, gross))
                .thenReturn(new RiskEvaluationResult(false, 35, "MEDIUM", "Carrier sanctions check failed"));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.RISK_REJECTED);
        assertThat(outcome.reasoning()).contains("approved=false");

        // Verify no payment order dispatched
        verify(paymentGrpcClient, never()).initiateDisbursement(any(), any(), any());
    }

    @Test
    @DisplayName("Anti-collusion check: Rejects advance when shipperId equals carrierId")
    void shouldRejectWhenShipperAndCarrierAreIdentical() {
        // Given
        String sharedId = carrierId.asString();
        ShipperId identicalShipper = ShipperId.of(sharedId);

        PricingDto pricing = new PricingDto(
                Money.of("900.00", Money.GBP),
                Money.of("20.00", Money.GBP),
                Money.of("80.00", Money.GBP),
                Money.of("1000.00", Money.GBP)
        );
        InvoiceDto collusiveInvoice = new InvoiceDto(
                invoiceId, loadId, identicalShipper, carrierId,
                "VAN", BigDecimal.TEN, "GBP", pricing, InvoiceStatus.EPOD_VERIFIED, Instant.now()
        );

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(collusiveInvoice));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.COLLUSION_DETECTED);
        assertThat(outcome.reasoning()).contains("Anti-collusion check failed");

        verify(riskGrpcClient, never()).evaluateCarrierRisk(any(), any(), any());
        verify(paymentGrpcClient, never()).initiateDisbursement(any(), any(), any());
    }

    @Test
    @DisplayName("Maximum Daily Advance Cap: Rejects when advance exceeds £50,000")
    void shouldRejectWhenNetAdvanceExceedsDailyCap() {
        // Given: £60,000 gross -> Net ~ £58,500 > £50,000
        Money gross = Money.of("60000.00", Money.GBP);
        InvoiceDto invoice = createVerifiedInvoice(gross);

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(invoice));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.LIMIT_EXCEEDED);
        assertThat(outcome.reasoning()).contains("exceeds max daily advance limit");

        verify(riskGrpcClient, never()).evaluateCarrierRisk(any(), any(), any());
        verify(paymentGrpcClient, never()).initiateDisbursement(any(), any(), any());
    }

    @Test
    @DisplayName("Ineligibility: Skips factoring when invoice status is not EPOD_VERIFIED")
    void shouldSkipFactoringWhenInvoiceStatusIsNotEpodVerified() {
        // Given: Invoice status is DRAFT
        PricingDto pricing = new PricingDto(
                Money.of("900.00", Money.GBP),
                Money.of("20.00", Money.GBP),
                Money.of("80.00", Money.GBP),
                Money.of("1000.00", Money.GBP)
        );
        InvoiceDto draftInvoice = new InvoiceDto(
                invoiceId, loadId, shipperId, carrierId,
                "VAN", BigDecimal.TEN, "GBP", pricing, InvoiceStatus.DRAFT, Instant.now()
        );

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(draftInvoice));

        // When
        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        // Then
        assertThat(outcome.status()).isEqualTo(FactoringStatus.INVOICE_NOT_ELIGIBLE);
        assertThat(outcome.reasoning()).contains("expected EPOD_VERIFIED");

        verify(riskGrpcClient, never()).evaluateCarrierRisk(any(), any(), any());
        verify(paymentGrpcClient, never()).initiateDisbursement(any(), any(), any());
    }

    @Test
    @DisplayName("Ineligibility: Handles missing invoice gracefully")
    void shouldHandleMissingInvoiceGracefully() {
        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.empty());

        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        assertThat(outcome.status()).isEqualTo(FactoringStatus.INVOICE_NOT_ELIGIBLE);
        assertThat(outcome.reasoning()).contains("Invoice not found");
    }

    @Test
    @DisplayName("Handles Payment gRPC initiation failure gracefully")
    void shouldHandlePaymentGrpcFailureGracefully() {
        Money gross = Money.of("1000.00", Money.GBP);
        InvoiceDto invoice = createVerifiedInvoice(gross);

        when(invoiceClient.getInvoiceByLoadId(loadId)).thenReturn(Optional.of(invoice));
        when(riskGrpcClient.evaluateCarrierRisk(carrierId, shipperId, gross))
                .thenReturn(new RiskEvaluationResult(true, 10, "LOW", "OK"));

        when(paymentGrpcClient.initiateDisbursement(any(), any(), any()))
                .thenThrow(new RuntimeException("Banking rail timeout"));

        FactoringPayoutOutcome outcome = worker.processDeliveryVerification(epodEvent);

        assertThat(outcome.status()).isEqualTo(FactoringStatus.PAYMENT_FAILED);
        assertThat(outcome.reasoning()).contains("Banking rail timeout");

        // Verify status was NOT updated to approved and event was NOT published
        verify(invoiceClient, never()).updateInvoiceStatus(any(), any());
        verify(eventPublisher, never()).publishApprovedPayout(any(), any(), any());
    }
}
