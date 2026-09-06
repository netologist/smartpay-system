package com.hozgan.smartpay.payout.service;

import com.hozgan.smartpay.common.event.EpodVerifiedEvent;
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
import com.hozgan.smartpay.payout.config.PayoutWorkerProperties;
import com.hozgan.smartpay.payout.domain.FactoringCalculationResult;
import com.hozgan.smartpay.payout.domain.FactoringPayoutOutcome;
import com.hozgan.smartpay.proto.payment.PaymentStatusProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Core Orchestrator for Carrier Factoring & Instant Payouts (STORY-004).
 * Coordinates:
 * 1. Invoice & Load Verification
 * 2. 2.5% Factoring Fee Calculation (AC-1)
 * 3. Carrier Risk & Fraud Assessment via gRPC (AC-2)
 * 4. Instant Payment Initiation via Payment gRPC (AC-3)
 * 5. Invoice State Transition to FACTORING_APPROVED
 * 6. Audit & FactoringPayoutApprovedEvent Dispatch
 */
@Service
public class FactoringPayoutWorker {

    private static final Logger log = LoggerFactory.getLogger(FactoringPayoutWorker.class);

    private final InvoiceClient invoiceClient;
    private final RiskGrpcClient riskGrpcClient;
    private final PaymentGrpcClient paymentGrpcClient;
    private final FactoringCalculationEngine calculationEngine;
    private final FactoringEventPublisher eventPublisher;
    private final PayoutWorkerProperties properties;

    public FactoringPayoutWorker(InvoiceClient invoiceClient,
                                 RiskGrpcClient riskGrpcClient,
                                 PaymentGrpcClient paymentGrpcClient,
                                 FactoringCalculationEngine calculationEngine,
                                 FactoringEventPublisher eventPublisher,
                                 PayoutWorkerProperties properties) {
        this.invoiceClient = invoiceClient;
        this.riskGrpcClient = riskGrpcClient;
        this.paymentGrpcClient = paymentGrpcClient;
        this.calculationEngine = calculationEngine;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Processes verified delivery ePOD event and executes factoring advance workflow.
     *
     * @param event delivery verified domain event
     * @return FactoringPayoutOutcome containing final state and reasoning
     */
    public FactoringPayoutOutcome processDeliveryVerification(EpodVerifiedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        LoadId loadId = event.loadId();
        CarrierId carrierId = event.carrierId();

        log.info("Processing factoring payout for loadId={}, carrierId={}", loadId, carrierId);

        // Step 1 & 2: Fetch and verify invoice for loadId
        Optional<InvoiceDto> invoiceOpt = invoiceClient.getInvoiceByLoadId(loadId);
        if (invoiceOpt.isEmpty()) {
            String msg = "Invoice not found for loadId: " + loadId;
            log.warn("{}", msg);
            return FactoringPayoutOutcome.notEligible(null, carrierId, msg);
        }

        InvoiceDto invoice = invoiceOpt.get();
        InvoiceId invoiceId = invoice.invoiceId();
        ShipperId shipperId = invoice.shipperId();

        if (invoice.status() != InvoiceStatus.EPOD_VERIFIED) {
            String msg = String.format("Invoice %s status is %s (expected EPOD_VERIFIED). Skipping factoring.",
                    invoiceId, invoice.status());
            log.info("{}", msg);
            return FactoringPayoutOutcome.notEligible(invoiceId, carrierId, msg);
        }

        // Anti-Collusion verification (NFR 2)
        if (!calculationEngine.verifyAntiCollusion(shipperId, carrierId)) {
            String msg = String.format("Anti-collusion check failed: Shipper %s matches Carrier %s for invoice %s",
                    shipperId, carrierId, invoiceId);
            log.warn("ALERT: {}", msg);
            return FactoringPayoutOutcome.collusionDetected(invoiceId, carrierId, msg);
        }

        // Step 3: Factoring Fee Calculation (AC-1)
        Money grossAmount = invoice.pricing().totalAmount();
        FactoringCalculationResult calculation = calculationEngine.calculate(
                grossAmount, properties.factoringFeePercentage());

        // Maximum Daily Advance Cap Check (NFR 2)
        if (!calculationEngine.verifyDailyAdvanceLimit(
                calculation.netPayoutAmount(), properties.maxDailyAdvancePence())) {
            String msg = String.format("Net payout %s exceeds max daily advance limit %d pence for carrier %s",
                    calculation.netPayoutAmount(), properties.maxDailyAdvancePence(), carrierId);
            log.warn("ALERT: {}", msg);
            return FactoringPayoutOutcome.limitExceeded(invoiceId, carrierId, grossAmount, msg);
        }

        // Step 4: Risk & Fraud Assessment via gRPC (AC-2)
        RiskEvaluationResult riskResult = riskGrpcClient.evaluateCarrierRisk(carrierId, shipperId, grossAmount);
        if (!riskResult.approved() || riskResult.riskScore() >= properties.riskScoreThreshold()) {
            String msg = String.format(
                    "Carrier %s credit risk evaluation rejected! Risk score=%d (threshold=%d), approved=%b, reasoning=%s",
                    carrierId, riskResult.riskScore(), properties.riskScoreThreshold(), riskResult.approved(), riskResult.reasoning());
            log.warn("ALERT: Fraud/Risk rejection: {}", msg);
            return FactoringPayoutOutcome.riskRejected(invoiceId, carrierId, grossAmount, msg);
        }

        // Step 5 & 6: Payment Initiation via Payment gRPC (AC-3)
        PaymentInitiationResult paymentResult;
        try {
            paymentResult = paymentGrpcClient.initiateDisbursement(
                    invoiceId, carrierId, calculation.netPayoutAmount());
        } catch (Exception e) {
            String msg = "Payment gRPC initiation failed for invoice " + invoiceId + ": " + e.getMessage();
            log.error("{}", msg, e);
            return FactoringPayoutOutcome.paymentFailed(invoiceId, carrierId, grossAmount, msg);
        }

        if (paymentResult.status() != PaymentStatusProto.INITIATED) {
            String msg = "Payment status is " + paymentResult.status() + " (expected INITIATED)";
            log.error("{}", msg);
            return FactoringPayoutOutcome.paymentFailed(invoiceId, carrierId, grossAmount, msg);
        }

        // Step 7: Update invoice state in invoice-service
        invoiceClient.updateInvoiceStatus(invoiceId, InvoiceStatus.FACTORING_APPROVED);

        // Step 8: Audit & Event Dispatch
        eventPublisher.publishApprovedPayout(invoiceId, carrierId, calculation);

        log.info("Factoring advance successfully executed: invoiceId={}, carrierId={}, paymentId={}, netPayout={}",
                invoiceId, carrierId, paymentResult.paymentId(), calculation.netPayoutAmount());

        return FactoringPayoutOutcome.approved(
                invoiceId, carrierId,
                calculation.grossAmount(), calculation.factoringFee(), calculation.netPayoutAmount(),
                paymentResult.paymentId()
        );
    }
}
