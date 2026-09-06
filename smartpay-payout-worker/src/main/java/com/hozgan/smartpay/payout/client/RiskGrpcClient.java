package com.hozgan.smartpay.payout.client;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskRequest;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskResponse;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class RiskGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(RiskGrpcClient.class);

    private final RiskServiceGrpc.RiskServiceBlockingStub riskBlockingStub;

    public RiskGrpcClient(RiskServiceGrpc.RiskServiceBlockingStub riskBlockingStub) {
        this.riskBlockingStub = riskBlockingStub;
    }

    public record RiskEvaluationResult(
            boolean approved,
            int riskScore,
            String riskTier,
            String reasoning
    ) {}

    /**
     * Calls RiskService gRPC to evaluate carrier creditworthiness and fraud propensity.
     * Protected by gRPC 3-second deadline and Resilience4j Circuit Breaker.
     */
    @CircuitBreaker(name = "riskService")
    public RiskEvaluationResult evaluateCarrierRisk(CarrierId carrierId, ShipperId shipperId, Money grossAmount) {
        Objects.requireNonNull(carrierId, "carrierId cannot be null");
        Objects.requireNonNull(shipperId, "shipperId cannot be null");
        Objects.requireNonNull(grossAmount, "grossAmount cannot be null");

        MoneyProto moneyProto = MoneyProto.newBuilder()
                .setCurrency(grossAmount.currency().getCurrencyCode())
                .setAmountInPence(grossAmount.toMinorUnits())
                .build();

        EvaluateCarrierRiskRequest request = EvaluateCarrierRiskRequest.newBuilder()
                .setCarrierId(carrierId.asString())
                .setShipperId(shipperId.asString())
                .setInvoiceAmount(moneyProto)
                .build();

        try {
            log.debug("Invoking RiskService.EvaluateCarrierRisk for carrier: {}, shipper: {}, amount: {}",
                    carrierId, shipperId, grossAmount);
            EvaluateCarrierRiskResponse response = riskBlockingStub
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .evaluateCarrierRisk(request);

            log.info("Risk evaluation result for carrier {}: approved={}, score={}, tier={}",
                    carrierId, response.getApproved(), response.getRiskScore(), response.getRiskTier());

            return new RiskEvaluationResult(
                    response.getApproved(),
                    response.getRiskScore(),
                    response.getRiskTier().name(),
                    response.getReasoning()
            );
        } catch (StatusRuntimeException e) {
            log.error("gRPC error calling RiskService for carrier {}: {}", carrierId, e.getStatus());
            throw e;
        }
    }
}
