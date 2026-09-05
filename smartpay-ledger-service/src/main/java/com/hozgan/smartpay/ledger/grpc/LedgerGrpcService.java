package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.util.UuidV7;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import com.hozgan.smartpay.ledger.service.LedgerDomainService;
import com.hozgan.smartpay.ledger.service.LedgerDomainService.JournalEntryItem;
import com.hozgan.smartpay.proto.ledger.GetBalanceRequest;
import com.hozgan.smartpay.proto.ledger.GetBalanceResponse;
import com.hozgan.smartpay.proto.ledger.HoldFundsRequest;
import com.hozgan.smartpay.proto.ledger.HoldFundsResponse;
import com.hozgan.smartpay.proto.ledger.JournalEntryLineProto;
import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import com.hozgan.smartpay.proto.ledger.PostJournalTransactionRequest;
import com.hozgan.smartpay.proto.ledger.PostJournalTransactionResponse;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldRequest;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldResponse;
import com.hozgan.smartpay.proto.ledger.TransferFundsRequest;
import com.hozgan.smartpay.proto.ledger.TransferFundsResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static com.hozgan.smartpay.ledger.grpc.LedgerGrpcMapper.toEntryType;
import static com.hozgan.smartpay.ledger.grpc.LedgerGrpcMapper.toMoney;
import static com.hozgan.smartpay.ledger.grpc.LedgerGrpcMapper.toMoneyProto;
import static com.hozgan.smartpay.ledger.grpc.LedgerGrpcMapper.toUUID;

/**
 * gRPC Service implementing {@link LedgerServiceGrpc.LedgerServiceImplBase}.
 * Provides high-performance, low-latency, HTTP/2 multiplexed access to the Ledger engine.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerGrpcService extends LedgerServiceGrpc.LedgerServiceImplBase {

    private final AccountBalanceService balanceService;
    private final LedgerDomainService ledgerDomainService;
    private final AccountRepository accountRepository;

    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> responseObserver) {
        try {
            UUID accountId = toUUID(request.getAccountId(), "account_id");
            AccountBalanceEntity balance = balanceService.getBalance(accountId);
            Currency currency = resolveCurrency(accountId);

            Money cleared = Money.ofMinor(balance.getClearedBalancePence(), currency);
            Money hold = Money.ofMinor(balance.getHoldBalancePence(), currency);
            Money available = balance.getAvailableBalance(currency);

            GetBalanceResponse response = GetBalanceResponse.newBuilder()
                    .setAccountId(accountId.toString())
                    .setClearedBalance(toMoneyProto(cleared))
                    .setHoldBalance(toMoneyProto(hold))
                    .setAvailableBalance(toMoneyProto(available))
                    .setVersion(balance.getVersion())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(LedgerGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }

    @Override
    public void postJournalTransaction(
            PostJournalTransactionRequest request,
            StreamObserver<PostJournalTransactionResponse> responseObserver) {
        try {
            if (request.getEntriesList().isEmpty()) {
                throw new IllegalArgumentException("PostJournalTransactionRequest must contain at least 2 entries");
            }

            List<JournalEntryItem> items = new ArrayList<>(request.getEntriesCount());
            for (JournalEntryLineProto entryProto : request.getEntriesList()) {
                UUID accountId = toUUID(entryProto.getAccountId(), "entry.account_id");
                items.add(new JournalEntryItem(
                        accountId,
                        toEntryType(entryProto.getEntryType()),
                        toMoney(entryProto.getAmount())));
            }

            String refType = request.getReferenceType().isBlank() ? "MANUAL_ADJUSTMENT" : request.getReferenceType();
            String refId = request.getReferenceId().isBlank() ? "REF-" + UuidV7.generate() : request.getReferenceId();
            String idempKey = request.getIdempotencyKey().isBlank() ? null : request.getIdempotencyKey();
            String desc = request.getDescription().isBlank() ? "gRPC Journal Posting" : request.getDescription();

            JournalTransactionEntity tx = ledgerDomainService.recordJournalTransaction(
                    refType, refId, idempKey, desc, items);

            PostJournalTransactionResponse response = PostJournalTransactionResponse.newBuilder()
                    .setTransactionId(tx.getId().toString())
                    .setStatus(tx.getStatus().name())
                    .setPostedAtEpochMs(tx.getPostedAt().toEpochMilli())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(LedgerGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }

    @Override
    public void transferFunds(
            TransferFundsRequest request,
            StreamObserver<TransferFundsResponse> responseObserver) {
        try {
            UUID sourceId = toUUID(request.getSourceAccountId(), "source_account_id");
            UUID targetId = toUUID(request.getTargetAccountId(), "target_account_id");
            Money amount = toMoney(request.getAmount());

            String idempotencyKey = request.getIdempotencyKey();
            if (idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotency_key cannot be null or blank");
            }

            String reference = request.getReference().isBlank() ? "GRPC-TRANSFER" : request.getReference();

            TransferResult result = balanceService.transfer(
                    sourceId,
                    targetId,
                    amount,
                    "TRANSFER",
                    reference,
                    idempotencyKey,
                    "gRPC Transfer: " + reference);

            TransferFundsResponse response = TransferFundsResponse.newBuilder()
                    .setTransactionId(result.transactionId().toString())
                    .setStatus(result.status().name())
                    .setSourceNewAvailableBalance(toMoneyProto(result.sourceAvailableBalance()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(LedgerGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }

    @Override
    public void holdFunds(
            HoldFundsRequest request,
            StreamObserver<HoldFundsResponse> responseObserver) {
        try {
            UUID accountId = toUUID(request.getAccountId(), "account_id");
            Money amount = toMoney(request.getAmount());

            AccountBalanceEntity balance = balanceService.holdFunds(accountId, amount);
            Currency currency = resolveCurrency(accountId);

            String holdId = request.getReferenceId().isBlank() ? UuidV7.generate().toString() : request.getReferenceId();

            HoldFundsResponse response = HoldFundsResponse.newBuilder()
                    .setHoldId(holdId)
                    .setNewHoldBalance(toMoneyProto(Money.ofMinor(balance.getHoldBalancePence(), currency)))
                    .setNewAvailableBalance(toMoneyProto(balance.getAvailableBalance(currency)))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(LedgerGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }

    @Override
    public void releaseHold(
            ReleaseHoldRequest request,
            StreamObserver<ReleaseHoldResponse> responseObserver) {
        try {
            UUID accountId = toUUID(request.getAccountId(), "account_id");
            Money amount = toMoney(request.getAmount());
            boolean capture = request.getCapture();

            AccountBalanceEntity balance = balanceService.releaseHold(accountId, amount, capture);
            Currency currency = resolveCurrency(accountId);

            String holdId = request.getHoldId().isBlank() ? "HOLD-RELEASED" : request.getHoldId();

            ReleaseHoldResponse response = ReleaseHoldResponse.newBuilder()
                    .setHoldId(holdId)
                    .setCaptured(capture)
                    .setNewAvailableBalance(toMoneyProto(balance.getAvailableBalance(currency)))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(LedgerGrpcExceptionHelper.toStatusRuntimeException(ex));
        }
    }

    private Currency resolveCurrency(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AccountEntity::getCurrency)
                .map(Currency::getInstance)
                .orElseThrow(() -> new AccountNotFoundException(AccountId.of(accountId)));
    }
}
