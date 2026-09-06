package com.hozgan.smartpay.payout.client;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.payout.client.RiskGrpcClient.RiskEvaluationResult;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskRequest;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskResponse;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import com.hozgan.smartpay.proto.risk.RiskTierProto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskGrpcClient — Unit Tests")
class RiskGrpcClientTest {

    @Mock
    private RiskServiceGrpc.RiskServiceBlockingStub riskBlockingStub;

    private RiskGrpcClient client;

    @BeforeEach
    void setUp() {
        when(riskBlockingStub.withDeadlineAfter(org.mockito.ArgumentMatchers.anyLong(), any()))
                .thenReturn(riskBlockingStub);
        client = new RiskGrpcClient(riskBlockingStub);
    }

    @Test
    @DisplayName("Successfully maps and executes EvaluateCarrierRisk request")
    void shouldEvaluateCarrierRiskSuccessfully() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money amount = Money.of("1000.00", Money.GBP);

        EvaluateCarrierRiskResponse grpcResponse = EvaluateCarrierRiskResponse.newBuilder()
                .setCarrierId(carrierId.asString())
                .setRiskScore(15)
                .setRiskTier(RiskTierProto.LOW)
                .setApproved(true)
                .setReasoning("Clean credit history")
                .build();

        when(riskBlockingStub.evaluateCarrierRisk(any(EvaluateCarrierRiskRequest.class)))
                .thenReturn(grpcResponse);

        RiskEvaluationResult result = client.evaluateCarrierRisk(carrierId, shipperId, amount);

        assertThat(result.approved()).isTrue();
        assertThat(result.riskScore()).isEqualTo(15);
        assertThat(result.riskTier()).isEqualTo("LOW");
        assertThat(result.reasoning()).isEqualTo("Clean credit history");

        ArgumentCaptor<EvaluateCarrierRiskRequest> captor = ArgumentCaptor.forClass(EvaluateCarrierRiskRequest.class);
        verify(riskBlockingStub).evaluateCarrierRisk(captor.capture());

        EvaluateCarrierRiskRequest captured = captor.getValue();
        assertThat(captured.getCarrierId()).isEqualTo(carrierId.asString());
        assertThat(captured.getShipperId()).isEqualTo(shipperId.asString());
        assertThat(captured.getInvoiceAmount().getCurrency()).isEqualTo("GBP");
        assertThat(captured.getInvoiceAmount().getAmountInPence()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("Propagates StatusRuntimeException on gRPC server failure")
    void shouldPropagateStatusRuntimeExceptionOnFailure() {
        CarrierId carrierId = CarrierId.generate();
        ShipperId shipperId = ShipperId.generate();
        Money amount = Money.of("500.00", Money.GBP);

        when(riskBlockingStub.evaluateCarrierRisk(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("Service offline")));

        assertThatThrownBy(() -> client.evaluateCarrierRisk(carrierId, shipperId, amount))
                .isInstanceOf(StatusRuntimeException.class)
                .hasMessageContaining("UNAVAILABLE");
    }
}
