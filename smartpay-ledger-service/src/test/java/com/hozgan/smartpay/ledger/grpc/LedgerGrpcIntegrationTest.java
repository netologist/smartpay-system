package com.hozgan.smartpay.ledger.grpc;

import com.hozgan.smartpay.common.model.enums.EntityType;
import com.hozgan.smartpay.ledger.TestcontainersConfiguration;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.AccountBalanceRepository;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import com.hozgan.smartpay.proto.common.MoneyProto;
import com.hozgan.smartpay.proto.ledger.EntryTypeProto;
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
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for {@link LedgerGrpcService} over real HTTP/2 gRPC channel.
 * Uses PostgreSQL 16 Testcontainers with Flyway migrations.
 */
import org.junit.jupiter.api.Tag;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("LedgerGrpcService — Full E2E Integration Tests (Testcontainers)")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class LedgerGrpcIntegrationTest {

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountBalanceRepository accountBalanceRepository;

    @Autowired
    private JournalTransactionRepository journalTransactionRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private ManagedChannel channel;
    private LedgerServiceGrpc.LedgerServiceBlockingStub blockingStub;

    private UUID sourceId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        int port = grpcServerLifecycle.getPort();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
        blockingStub = LedgerServiceGrpc.newBlockingStub(channel);

        // Generate unique test accounts
        String sSuffix = UUID.randomUUID().toString().substring(0, 8);
        String tSuffix = UUID.randomUUID().toString().substring(0, 8);
        AccountEntity src = new AccountEntity("SRC-" + sSuffix, UUID.randomUUID(), EntityType.SHIPPER, "GBP");
        AccountEntity tgt = new AccountEntity("TGT-" + tSuffix, UUID.randomUUID(), EntityType.CARRIER, "GBP");
        accountRepository.saveAll(List.of(src, tgt));

        sourceId = src.getId();
        targetId = tgt.getId();

        // Seed balances: Source = £1,000 (100,000 pence), Target = £0
        accountBalanceRepository.saveAll(List.of(
                new AccountBalanceEntity(sourceId, 100_000L, 0L),
                new AccountBalanceEntity(targetId, 0L, 0L)
        ));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // 1. GetBalance RPC
    // =========================================================================

    @Test
    @DisplayName("RPC GetBalance: queries real database balances correctly")
    void testGetBalance_success() {
        GetBalanceRequest request = GetBalanceRequest.newBuilder()
                .setAccountId(sourceId.toString())
                .build();

        GetBalanceResponse response = blockingStub.getBalance(request);

        assertThat(response.getAccountId()).isEqualTo(sourceId.toString());
        assertThat(response.getClearedBalance().getAmountInPence()).isEqualTo(100_000L);
        assertThat(response.getClearedBalance().getCurrency()).isEqualTo("GBP");
        assertThat(response.getHoldBalance().getAmountInPence()).isZero();
        assertThat(response.getAvailableBalance().getAmountInPence()).isEqualTo(100_000L);
        assertThat(response.getVersion()).isZero();
    }

    @Test
    @DisplayName("RPC GetBalance: unknown account returns Status.NOT_FOUND")
    void testGetBalance_accountNotFound() {
        GetBalanceRequest request = GetBalanceRequest.newBuilder()
                .setAccountId(UUID.randomUUID().toString())
                .build();

        assertThatThrownBy(() -> blockingStub.getBalance(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                });
    }

    // =========================================================================
    // 2. TransferFunds RPC
    // =========================================================================

    @Test
    @DisplayName("RPC TransferFunds: atomic double-entry balance transfer updates balances and journals")
    void testTransferFunds_success() {
        String idempotencyKey = "GRPC-TX-" + UUID.randomUUID();

        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(sourceId.toString())
                .setTargetAccountId(targetId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(25_000L).build()) // £250.00
                .setReference("FREIGHT-LOAD-01")
                .setIdempotencyKey(idempotencyKey)
                .build();

        TransferFundsResponse response = blockingStub.transferFunds(request);

        assertThat(response.getTransactionId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getSourceNewAvailableBalance().getAmountInPence()).isEqualTo(75_000L); // £750.00

        // Verify DB state
        AccountBalanceEntity srcBalance = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        AccountBalanceEntity tgtBalance = accountBalanceRepository.findByAccountId(targetId).orElseThrow();
        assertThat(srcBalance.getClearedBalancePence()).isEqualTo(75_000L);
        assertThat(tgtBalance.getClearedBalancePence()).isEqualTo(25_000L);

        // Verify Journal Transactions and Entries
        UUID txId = UUID.fromString(response.getTransactionId());
        Optional<JournalTransactionEntity> journalTx = journalTransactionRepository.findById(txId);
        assertThat(journalTx).isPresent();
        assertThat(journalTx.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("RPC TransferFunds: idempotent retry returns cached transaction without duplicate debit")
    void testTransferFunds_idempotent() {
        String idempotencyKey = "GRPC-IDEMP-" + UUID.randomUUID();

        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(sourceId.toString())
                .setTargetAccountId(targetId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(10_000L).build()) // £100.00
                .setReference("FREIGHT-LOAD-02")
                .setIdempotencyKey(idempotencyKey)
                .build();

        TransferFundsResponse first = blockingStub.transferFunds(request);
        TransferFundsResponse second = blockingStub.transferFunds(request);

        assertThat(second.getTransactionId()).isEqualTo(first.getTransactionId());
        assertThat(second.getSourceNewAvailableBalance().getAmountInPence()).isEqualTo(90_000L);

        AccountBalanceEntity srcBalance = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        assertThat(srcBalance.getClearedBalancePence()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("RPC TransferFunds: insufficient funds throws Status.FAILED_PRECONDITION")
    void testTransferFunds_insufficientFunds() {
        TransferFundsRequest request = TransferFundsRequest.newBuilder()
                .setSourceAccountId(sourceId.toString())
                .setTargetAccountId(targetId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(200_000L).build()) // £2,000.00 > £1,000
                .setIdempotencyKey("GRPC-FAIL-" + UUID.randomUUID())
                .build();

        assertThatThrownBy(() -> blockingStub.transferFunds(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
                    assertThat(sre.getMessage()).contains("insufficient funds");
                });
    }

    // =========================================================================
    // 3. HoldFunds & ReleaseHold RPC
    // =========================================================================

    @Test
    @DisplayName("RPC HoldFunds and ReleaseHold: reserve and capture funds")
    void testHoldAndReleaseFunds_capture() {
        // 1. Hold £300
        HoldFundsRequest holdReq = HoldFundsRequest.newBuilder()
                .setAccountId(sourceId.toString())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(30_000L).build())
                .setReferenceId("HOLD-E2E-1")
                .build();

        HoldFundsResponse holdRes = blockingStub.holdFunds(holdReq);
        assertThat(holdRes.getNewHoldBalance().getAmountInPence()).isEqualTo(30_000L);
        assertThat(holdRes.getNewAvailableBalance().getAmountInPence()).isEqualTo(70_000L);

        // 2. Release hold with capture = true
        ReleaseHoldRequest relReq = ReleaseHoldRequest.newBuilder()
                .setAccountId(sourceId.toString())
                .setHoldId(holdRes.getHoldId())
                .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(30_000L).build())
                .setCapture(true)
                .build();

        ReleaseHoldResponse relRes = blockingStub.releaseHold(relReq);
        assertThat(relRes.getCaptured()).isTrue();
        assertThat(relRes.getNewAvailableBalance().getAmountInPence()).isEqualTo(70_000L);

        // Verify DB balances
        AccountBalanceEntity srcBalance = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        assertThat(srcBalance.getClearedBalancePence()).isEqualTo(70_000L);
        assertThat(srcBalance.getHoldBalancePence()).isZero();
    }

    // =========================================================================
    // 4. PostJournalTransaction RPC
    // =========================================================================

    @Test
    @DisplayName("RPC PostJournalTransaction: balanced multi-entry posting succeeds")
    void testPostJournalTransaction_balanced() {
        PostJournalTransactionRequest request = PostJournalTransactionRequest.newBuilder()
                .setReferenceType("INTERCOMPANY_SETTLEMENT")
                .setReferenceId("IC-2026-99")
                .setIdempotencyKey("JOURNAL-IDEMP-" + UUID.randomUUID())
                .setDescription("Intercompany fleet settlement")
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(sourceId.toString())
                        .setEntryType(EntryTypeProto.DEBIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(45_000L).build()) // £450.00
                        .build())
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(targetId.toString())
                        .setEntryType(EntryTypeProto.CREDIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(45_000L).build()) // £450.00
                        .build())
                .build();

        PostJournalTransactionResponse response = blockingStub.postJournalTransaction(request);

        assertThat(response.getTransactionId()).isNotBlank();
        assertThat(response.getStatus()).isEqualTo("POSTED");

        UUID txId = UUID.fromString(response.getTransactionId());
        assertThat(journalTransactionRepository.findById(txId)).isPresent();
        assertThat(journalEntryRepository.findByTransactionId(txId)).hasSize(2);
    }

    @Test
    @DisplayName("RPC PostJournalTransaction: unbalanced entries throw Status.INVALID_ARGUMENT")
    void testPostJournalTransaction_unbalanced() {
        PostJournalTransactionRequest request = PostJournalTransactionRequest.newBuilder()
                .setReferenceType("UNBALANCED")
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(sourceId.toString())
                        .setEntryType(EntryTypeProto.DEBIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(50_000L).build()) // £500
                        .build())
                .addEntries(JournalEntryLineProto.newBuilder()
                        .setAccountId(targetId.toString())
                        .setEntryType(EntryTypeProto.CREDIT)
                        .setAmount(MoneyProto.newBuilder().setCurrency("GBP").setAmountInPence(40_000L).build()) // £400
                        .build())
                .build();

        assertThatThrownBy(() -> blockingStub.postJournalTransaction(request))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(ex -> {
                    StatusRuntimeException sre = (StatusRuntimeException) ex;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(sre.getMessage()).contains("unbalanced");
                });
    }
}
