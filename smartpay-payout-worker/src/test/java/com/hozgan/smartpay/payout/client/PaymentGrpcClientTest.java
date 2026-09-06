package com.hozgan.smartpay.payout.client;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.InvoiceId;
import com.hozgan.smartpay.payout.client.PaymentGrpcClient.PaymentInitiationResult;
import com.hozgan.smartpay.payout.config.PayoutWorkerProperties;
import com.hozgan.smartpay.proto.payment.InitiatePaymentRequest;
import com.hozgan.smartpay.proto.payment.InitiatePaymentResponse;
import com.hozgan.smartpay.proto.payment.PaymentMethodProto;
import com.hozgan.smartpay.proto.payment.PaymentServiceGrpc;
import com.hozgan.smartpay.proto.payment.PaymentStatusProto;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGrpcClient — Unit Tests")
class PaymentGrpcClientTest {

    @Mock
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentBlockingStub;

    private PayoutWorkerProperties properties;
    private PaymentGrpcClient client;

    @BeforeEach
    void setUp() {
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
        org.mockito.Mockito.when(paymentBlockingStub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(paymentBlockingStub);
        client = new PaymentGrpcClient(paymentBlockingStub, properties);
    }

    @Test
    @DisplayName("AC-3: Successfully maps and executes InitiatePayment with idempotency key")
    void shouldInitiateDisbursementSuccessfully() {
        InvoiceId invoiceId = InvoiceId.generate();
        CarrierId carrierId = CarrierId.generate();
        Money netPayout = Money.of("975.00", Money.GBP);

        InitiatePaymentResponse grpcResponse = InitiatePaymentResponse.newBuilder()
                .setPaymentId("PAY-12345")
                .setStatus(PaymentStatusProto.INITIATED)
                .setEndToEndId("E2E-SMARTPAY-FACT-" + invoiceId.asString())
                .setInitiatedAtEpochMs(System.currentTimeMillis())
                .build();

        when(paymentBlockingStub.initiatePayment(any(InitiatePaymentRequest.class)))
                .thenReturn(grpcResponse);

        PaymentInitiationResult result = client.initiateDisbursement(invoiceId, carrierId, netPayout);

        assertThat(result.paymentId()).isEqualTo("PAY-12345");
        assertThat(result.status()).isEqualTo(PaymentStatusProto.INITIATED);
        assertThat(result.endToEndId()).isEqualTo("E2E-SMARTPAY-FACT-" + invoiceId.asString());

        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(paymentBlockingStub).initiatePayment(captor.capture());

        InitiatePaymentRequest captured = captor.getValue();
        assertThat(captured.getTenantId()).isEqualTo("TENANT-UK-01");
        assertThat(captured.getIdempotencyKey()).isEqualTo("FACTORING-ADVANCE-INV-" + invoiceId.asString());
        assertThat(captured.getDebtorAccountId()).isEqualTo("0191c7a2-9b24-7f11-9a1c-3d842b10a512");
        assertThat(captured.getCreditorAccountId()).isEqualTo(carrierId.asString());
        assertThat(captured.getAmount().getCurrency()).isEqualTo("GBP");
        assertThat(captured.getAmount().getAmountInPence()).isEqualTo(97500L);
        assertThat(captured.getPaymentMethod()).isEqualTo(PaymentMethodProto.FASTER_PAYMENTS);
        assertThat(captured.getEndToEndId()).isEqualTo("E2E-SMARTPAY-FACT-" + invoiceId.asString());
    }

    @Test
    @DisplayName("Propagates StatusRuntimeException on Payment gRPC error")
    void shouldPropagateStatusRuntimeExceptionOnFailure() {
        InvoiceId invoiceId = InvoiceId.generate();
        CarrierId carrierId = CarrierId.generate();
        Money netPayout = Money.of("975.00", Money.GBP);

        when(paymentBlockingStub.initiatePayment(any()))
                .thenThrow(new StatusRuntimeException(Status.FAILED_PRECONDITION.withDescription("Escrow funds held")));

        assertThatThrownBy(() -> client.initiateDisbursement(invoiceId, carrierId, netPayout))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("FAILED_PRECONDITION");
    }
}
