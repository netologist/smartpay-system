package com.hozgan.smartpay.payout.client;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.payout.config.PayoutWorkerProperties;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.payment.InitiatePaymentRequest;
import com.hozgan.smartpay.proto.payment.InitiatePaymentResponse;
import com.hozgan.smartpay.proto.payment.PaymentMethodProto;
import com.hozgan.smartpay.proto.payment.PaymentServiceGrpc;
import com.hozgan.smartpay.proto.payment.PaymentStatusProto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcClient.class);

    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentBlockingStub;
    private final PayoutWorkerProperties properties;

    public PaymentGrpcClient(PaymentServiceGrpc.PaymentServiceBlockingStub paymentBlockingStub,
                             PayoutWorkerProperties properties) {
        this.paymentBlockingStub = paymentBlockingStub;
        this.properties = properties;
    }

    public record PaymentInitiationResult(
            String paymentId,
            PaymentStatusProto status,
            String endToEndId
    ) {}

    /**
     * Calls PaymentService gRPC to initiate Faster Payments disbursement.
     * Protected by 5-second gRPC deadline and Resilience4j Circuit Breaker.
     * Enforces AC-3: idempotency_key = "FACTORING-ADVANCE-INV-" + invoiceId
     */
    @CircuitBreaker(name = "paymentService")
    public PaymentInitiationResult initiateDisbursement(InvoiceId invoiceId, CarrierId carrierId, Money netPayoutAmount) {
        Objects.requireNonNull(invoiceId, "invoiceId cannot be null");
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(netPayoutAmount, "netPayoutAmount cannot be null");

        String idempotencyKey = "FACTORING-ADVANCE-INV-" + invoiceId.asString();
        String paymentReference = "ADV-INV-" + invoiceId.asString().substring(0, Math.min(8, invoiceId.asString().length()));
        String endToEndId = "E2E-SMARTPAY-FACT-" + invoiceId.asString();

        MoneyProto moneyProto = MoneyProto.newBuilder()
                .setCurrency(netPayoutAmount.currency().getCurrencyCode())
                .setAmountInPence(netPayoutAmount.toMinorUnits())
                .build();

        InitiatePaymentRequest request = InitiatePaymentRequest.newBuilder()
                .setTenantId(properties.tenantId())
                .setIdempotencyKey(idempotencyKey)
                .setDebtorAccountId(properties.escrowAccountId())
                .setCreditorAccountId(carrierId.asString())
                .setAmount(moneyProto)
                .setPaymentMethod(PaymentMethodProto.FASTER_PAYMENTS)
                .setPaymentReference(paymentReference)
                .setEndToEndId(endToEndId)
                .build();

        try {
            log.info("Initiating factoring payment: idempotencyKey={}, debtor={}, creditor={}, amount={}",
                    idempotencyKey, properties.escrowAccountId(), carrierId, netPayoutAmount);

            InitiatePaymentResponse response = paymentBlockingStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .initiatePayment(request);

            log.info("Payment initiated successfully: paymentId={}, status={}",
                    response.getPaymentId(), response.getStatus());

            return new PaymentInitiationResult(
                    response.getPaymentId(),
                    response.getStatus(),
                    response.getEndToEndId()
            );
        } catch (StatusRuntimeException e) {
            log.error("gRPC error calling PaymentService for invoice {}: {}", invoiceId, e.getStatus());
            throw e;
        }
    }
}
