package com.hozgan.smartpay.risk.grpc;

import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.CarrierId;
import com.hozgan.smartpay.common.model.id.ShipperId;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskRequest;
import com.hozgan.smartpay.proto.risk.EvaluateCarrierRiskResponse;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import com.hozgan.smartpay.risk.service.EvaluateCarrierRiskCommand;
import com.hozgan.smartpay.risk.service.RiskEvaluationResult;
import com.hozgan.smartpay.risk.service.RiskEvaluationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.hozgan.smartpay.risk.grpc.RiskGrpcMapper.toMoney;
import static com.hozgan.smartpay.risk.grpc.RiskGrpcMapper.toProto;

/**
 * gRPC Service implementing {@link RiskServiceGrpc.RiskServiceImplBase}.
 * Evaluates carrier credit risk, factoring ceilings, and fraud propensity scores over HTTP/2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskGrpcService extends RiskServiceGrpc.RiskServiceImplBase {

    private final RiskEvaluationService riskEvaluationService;

    @Override
    public void evaluateCarrierRisk(EvaluateCarrierRiskRequest request,
                                    StreamObserver<EvaluateCarrierRiskResponse> responseObserver) {
        try {
            if (request.getCarrierId().isBlank()) {
                throw new IllegalArgumentException("carrier_id cannot be null or blank");
            }
            if (request.getShipperId().isBlank()) {
                throw new IllegalArgumentException("shipper_id cannot be null or blank");
            }
            if (!request.hasInvoiceAmount()) {
                throw new IllegalArgumentException("invoice_amount is required");
            }

            CarrierId carrierId = CarrierId.of(UUID.fromString(request.getCarrierId().trim()));
            ShipperId shipperId = ShipperId.of(UUID.fromString(request.getShipperId().trim()));
            Money invoiceAmount = toMoney(request.getInvoiceAmount());

            Optional<Instant> invoiceCreatedAt = request.hasInvoiceCreatedAtEpochMs()
                    ? Optional.of(Instant.ofEpochMilli(request.getInvoiceCreatedAtEpochMs()))
                    : Optional.empty();

            Optional<Instant> epodVerifiedAt = request.hasEpodVerifiedAtEpochMs()
                    ? Optional.of(Instant.ofEpochMilli(request.getEpodVerifiedAtEpochMs()))
                    : Optional.empty();

            Optional<String> clientIp = request.hasClientIp() && !request.getClientIp().isBlank()
                    ? Optional.of(request.getClientIp().trim())
                    : Optional.empty();

            Optional<String> bankAccount = request.hasBankAccountNumber() && !request.getBankAccountNumber().isBlank()
                    ? Optional.of(request.getBankAccountNumber().trim())
                    : Optional.empty();

            EvaluateCarrierRiskCommand command = new EvaluateCarrierRiskCommand(
                    carrierId,
                    shipperId,
                    invoiceAmount,
                    invoiceCreatedAt,
                    epodVerifiedAt,
                    clientIp,
                    bankAccount
            );

            RiskEvaluationResult result = riskEvaluationService.evaluateCarrierRisk(command);

            EvaluateCarrierRiskResponse response = EvaluateCarrierRiskResponse.newBuilder()
                    .setCarrierId(result.carrierId().asString())
                    .setRiskScore(result.riskScore())
                    .setRiskTier(toProto(result.riskTier()))
                    .setApproved(result.approved())
                    .setReasoning(result.reasoning())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Error evaluating carrier risk: {}", ex.getMessage(), ex);
            responseObserver.onError(RiskGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }
}
