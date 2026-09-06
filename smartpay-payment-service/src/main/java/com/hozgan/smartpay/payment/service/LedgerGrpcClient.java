package com.hozgan.smartpay.payment.service;

import com.hozgan.smartpay.common.exception.InsufficientFundsForPaymentException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.model.id.EndToEndId;
import com.hozgan.smartpay.proto.ledger.HoldFundsRequest;
import com.hozgan.smartpay.proto.ledger.HoldFundsResponse;
import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldRequest;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerGrpcClient {

    private final LedgerServiceGrpc.LedgerServiceBlockingStub ledgerStub;

    /**
     * Places an authorization hold on the debtor account via the Ledger gRPC service.
     *
     * @throws InsufficientFundsForPaymentException if the Ledger rejects with FAILED_PRECONDITION
     * @throws StatusRuntimeException               for all other gRPC errors (re-thrown after logging)
     */
    public HoldResult holdFunds(AccountId accountId, Money amount,
                                EndToEndId endToEndId, String idempotencyKey) {
        HoldFundsRequest request = HoldFundsRequest.newBuilder()
                .setAccountId(accountId.asString())
                .setAmount(PaymentGrpcMapper.toMoneyProto(amount))
                .setReferenceId(endToEndId.value())
                .setIdempotencyKey(idempotencyKey)
                .build();
        try {
            HoldFundsResponse response = ledgerStub.holdFunds(request);
            return new HoldResult(
                    response.getHoldId(),
                    PaymentGrpcMapper.toMoney(response.getNewHoldBalance()),
                    PaymentGrpcMapper.toMoney(response.getNewAvailableBalance()));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                throw new InsufficientFundsForPaymentException(accountId);
            }
            log.error("Ledger HoldFunds gRPC error for account {}: {}", accountId, e.getStatus());
            throw e;
        }
    }

    /**
     * Releases a previously placed hold, optionally capturing (settling) the amount.
     */
    public ReleaseResult releaseHold(AccountId accountId, String holdId, Money amount, boolean capture) {
        ReleaseHoldRequest request = ReleaseHoldRequest.newBuilder()
                .setAccountId(accountId.asString())
                .setHoldId(holdId)
                .setAmount(PaymentGrpcMapper.toMoneyProto(amount))
                .setCapture(capture)
                .build();
        ReleaseHoldResponse response = ledgerStub.releaseHold(request);
        return new ReleaseResult(
                response.getHoldId(),
                response.getCaptured(),
                PaymentGrpcMapper.toMoney(response.getNewAvailableBalance()));
    }

    public record HoldResult(String holdId, Money newHoldBalance, Money newAvailableBalance) {}

    public record ReleaseResult(String holdId, boolean captured, Money newAvailableBalance) {}
}
