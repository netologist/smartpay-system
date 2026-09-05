package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.exception.UnbalancedJournalTransactionException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntityType;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.common.model.id.AccountId;
import com.hozgan.smartpay.common.util.UuidV7;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import com.hozgan.smartpay.ledger.service.LedgerDomainService;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.ledger.EntryTypeProto;
import com.hozgan.smartpay.proto.ledger.GetBalanceRequest;
import com.hozgan.smartpay.proto.ledger.GetBalanceResponse;
import com.hozgan.smartpay.proto.ledger.HoldFundsRequest;
import com.hozgan.smartpay.proto.ledger.HoldFundsResponse;
import com.hozgan.smartpay.proto.ledger.JournalEntryLineProto;
import com.hozgan.smartpay.proto.ledger.PostJournalTransactionRequest;
import com.hozgan.smartpay.proto.ledger.PostJournalTransactionResponse;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldRequest;
import com.hozgan.smartpay.proto.ledger.ReleaseHoldResponse;
import com.hozgan.smartpay.proto.ledger.TransferFundsRequest;
import com.hozgan.smartpay.proto.ledger.TransferFundsResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerGrpcServiceTest {

    @Mock
    private AccountBalanceService balanceService;
    @Mock
    private LedgerDomainService ledgerDomainService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private StreamObserver<GetBalanceResponse> getBalanceObserver;
    @Mock
    private StreamObserver<TransferFundsResponse> transferFundsObserver;
    @Mock
    private StreamObserver<PostJournalTransactionResponse> postJournalObserver;
    @Mock
    private StreamObserver<HoldFundsResponse> holdFundsObserver;
    @Mock
    private StreamObserver<ReleaseHoldResponse> releaseHoldObserver;

    private LedgerGrpcService grpcService;

    private UUID accountId;
    private UUID targetAccountId;
    private AccountEntity accountEntity;

    @BeforeEach
    void setUp() {
        grpcService = new LedgerGrpcService(balanceService, ledgerDomainService, accountRepository);
        accountId = UuidV7.generate();
        targetAccountId = UuidV7.generate();

        accountEntity = new AccountEntity("ACC-001", UUID.randomUUID(), EntityType.CARRIER, "GBP");
        accountEntity.setId(accountId);
    }

    // =========================================================================
    // GetBalance Tests
    // =========================================================================

    @Test
    @DisplayName("getBalance: returns account balance and version successfully")
    void getBalance_success() {
        AccountBalanceEntity balance = new AccountBalanceEntity(accountId, 100_000L, 20_000L);
        balance.setVersion(3L);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountEntity));
        when(balanceService.getBalance(accountId)).thenReturn(balance);

        GetBalanceRequest request = GetBalanceRequest.newBuilder()
                .setAccountId(accountId.toString())
                .build();

        grpcService.getBalance(request, getBalanceObserver);

        ArgumentCaptor<GetBalanceResponse> captor = ArgumentCaptor.forClass(GetBalanceResponse.class);
        verify(getBalanceObserver).onNext(captor.capture());
        verify(getBalanceObserver).onCompleted();

        GetBalanceResponse response = captor.getValue();
        assertThat(response.getAccountId()).isEqualTo(accountId.toString());
        assertThat(response.getClearedBalance().getAmountInPence()).isEqualTo(100_000L);
        assertThat(response.getClearedBalance().getCurrency()).isEqualTo("GBP");
        assertThat(response.getHoldBalance().getAmountInPence()).isEqualTo(20_000L);
        assertThat(response.getAvailableBalance().getAmountInPence()).isEqualTo(80_000L);
        assertThat(response.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getBalance: missing account translates to gRPC Status.NOT_FOUND")
    void getBalance_accountNotFound() {
        when(balanceService.getBalance(accountId))
                .thenThrow(new AccountNotFoundException(AccountId.of(accountId)));

        GetBalanceRequest request = GetBalanceRequest.newBuilder()
                .setAccountId(accountId.toString())
                .build();

        grpcService.getBalance(request, getBalanceObserver);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(getBalanceObserver).onError(errorCaptor.capture());
        verify(getBalanceObserver, never()).onCompleted();

        assertThat(errorCaptor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) errorCaptor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }

    // =========================================================================
    // TransferFunds Tests
    // =========================================================================

    @Test
    @DisplayName("transferFunds: executes transfer and returns updated available balance")
    void transferFunds_success() {
        UUID txId = UuidV7.generate();
        TransferResult result = new TransferResult(
                txId,
                JournalStatus.POSTED,
                Instant.now(),
                Money.ofGBP("750.00"),
                Money.ofGBP("250.00"),
                Money.ofGBP("250.00"));

        when(balanceService.transfer(
                eq(accountId), eq(targetAccountId), any(Money.class),
                eq("TRANSFER"), eq("INV-100"), eq("KEY-123"), any()))
                .thenReturn(result);

        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(accountId.toString())
                .setTargetAccountId(targetAccountId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(25000).build())
                .setReference("INV-100")
                .setIdempotencyKey("KEY-123")
                .build();

        grpcService.transferFunds(request, transferFundsObserver);

        ArgumentCaptor<TransferFundsResponse> captor = ArgumentCaptor.forClass(TransferFundsResponse.class);
        verify(transferFundsObserver).onNext(captor.capture());
        verify(transferFundsObserver).onCompleted();

        TransferFundsResponse response = captor.getValue();
        assertThat(response.getTransactionId()).isEqualTo(txId.toString());
        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getSourceNewAvailableBalance().getAmountInPence()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("transferFunds: insufficient funds translates to Status.FAILED_PRECONDITION")
    void transferFunds_insufficientFunds() {
        when(balanceService.transfer(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(AccountId.of(accountId), Money.ofGBP("500.00"), Money.ofGBP("100.00")));

        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(accountId.toString())
                .setTargetAccountId(targetAccountId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(50000).build())
                .setIdempotencyKey("KEY-ERR")
                .build();

        grpcService.transferFunds(request, transferFundsObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(transferFundsObserver).onError(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("transferFunds: currency mismatch translates to Status.INVALID_ARGUMENT")
    void transferFunds_currencyMismatch() {
        when(balanceService.transfer(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CurrencyMismatchException(Currency.getInstance("GBP"), Currency.getInstance("EUR")));

        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(accountId.toString())
                .setTargetAccountId(targetAccountId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("EUR").setAmountInPence(1000).build())
                .setIdempotencyKey("KEY-MISMATCH")
                .build();

        grpcService.transferFunds(request, transferFundsObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(transferFundsObserver).onError(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // =========================================================================
    // PostJournalTransaction Tests
    // =========================================================================

    @Test
    @DisplayName("postJournalTransaction: balanced entries recorded successfully")
    void postJournalTransaction_success() {
        UUID txId = UuidV7.generate();
        JournalTransactionEntity tx = new JournalTransactionEntity(
                "FREIGHT_CHARGE", "REF-001", "IDEMP-001", JournalStatus.POSTED, "Freight invoice");
        tx.setId(txId);
        tx.setPostedAt(Instant.now());

        when(ledgerDomainService.recordJournalTransaction(any(), any(), any(), any(), any()))
                .thenReturn(tx);

        PostJournalTransactionRequest request = PostJournalTransactionRequest.newBuilder()
                .setReferenceType("FREIGHT_CHARGE")
                .setReferenceId("REF-001")
                .setIdempotencyKey("IDEMP-001")
                .setDescription("Freight invoice")
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(accountId.toString())
                        .setEntryType(EntryTypeProto.DEBIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(15000).build())
                        .build())
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(targetAccountId.toString())
                        .setEntryType(EntryTypeProto.CREDIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(15000).build())
                        .build())
                .build();

        grpcService.postJournalTransaction(request, postJournalObserver);

        ArgumentCaptor<PostJournalTransactionResponse> captor = ArgumentCaptor.forClass(PostJournalTransactionResponse.class);
        verify(postJournalObserver).onNext(captor.capture());
        verify(postJournalObserver).onCompleted();

        PostJournalTransactionResponse response = captor.getValue();
        assertThat(response.getTransactionId()).isEqualTo(txId.toString());
        assertThat(response.getStatus()).isEqualTo("POSTED");
    }

    @Test
    @DisplayName("postJournalTransaction: unbalanced entries translate to Status.INVALID_ARGUMENT")
    void postJournalTransaction_unbalanced() {
        when(ledgerDomainService.recordJournalTransaction(any(), any(), any(), any(), any()))
                .thenThrow(new UnbalancedJournalTransactionException(Money.ofGBP("150.00"), Money.ofGBP("100.00")));

        PostJournalTransactionRequest request = PostJournalTransactionRequest.newBuilder()
                .setReferenceType("UNBALANCED")
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(accountId.toString())
                        .setEntryType(EntryTypeProto.DEBIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(15000).build())
                        .build())
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(targetAccountId.toString())
                        .setEntryType(EntryTypeProto.CREDIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(10000).build())
                        .build())
                .build();

        grpcService.postJournalTransaction(request, postJournalObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(postJournalObserver).onError(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    // =========================================================================
    // HoldFunds and ReleaseHold Tests
    // =========================================================================

    @Test
    @DisplayName("holdFunds: places hold and returns updated hold balance")
    void holdFunds_success() {
        AccountBalanceEntity balance = new AccountBalanceEntity(accountId, 100_000L, 25_000L);
        when(balanceService.holdFunds(eq(accountId), any(Money.class))).thenReturn(balance);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountEntity));

        HoldFundsRequest request = HoldFundsRequest.newBuilder()
                .setAccountId(accountId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(25000).build())
                .setReferenceId("HOLD-REF-1")
                .build();

        grpcService.holdFunds(request, holdFundsObserver);

        ArgumentCaptor<HoldFundsResponse> captor = ArgumentCaptor.forClass(HoldFundsResponse.class);
        verify(holdFundsObserver).onNext(captor.capture());
        verify(holdFundsObserver).onCompleted();

        HoldFundsResponse response = captor.getValue();
        assertThat(response.getHoldId()).isEqualTo("HOLD-REF-1");
        assertThat(response.getNewHoldBalance().getAmountInPence()).isEqualTo(25_000L);
        assertThat(response.getNewAvailableBalance().getAmountInPence()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("releaseHold: releases hold and returns updated available balance")
    void releaseHold_success() {
        AccountBalanceEntity balance = new AccountBalanceEntity(accountId, 100_000L, 0L);
        when(balanceService.releaseHold(eq(accountId), any(Money.class), eq(true))).thenReturn(balance);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(accountEntity));

        ReleaseHoldRequest request = ReleaseHoldRequest.newBuilder()
                .setAccountId(accountId.toString())
                .setHoldId("HOLD-REF-1")
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(25000).build())
                .setCapture(true)
                .build();

        grpcService.releaseHold(request, releaseHoldObserver);

        ArgumentCaptor<ReleaseHoldResponse> captor = ArgumentCaptor.forClass(ReleaseHoldResponse.class);
        verify(releaseHoldObserver).onNext(captor.capture());
        verify(releaseHoldObserver).onCompleted();

        ReleaseHoldResponse response = captor.getValue();
        assertThat(response.getHoldId()).isEqualTo("HOLD-REF-1");
        assertThat(response.getCaptured()).isTrue();
        assertThat(response.getNewAvailableBalance().getAmountInPence()).isEqualTo(100_000L);
    }
}
