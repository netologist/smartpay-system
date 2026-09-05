package com.hozgan.smartpay.ledger;

import com.hozgan.smartpay.common.exception.AccountNotFoundException;
import com.hozgan.smartpay.common.exception.CurrencyMismatchException;
import com.hozgan.smartpay.common.exception.InsufficientFundsException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntityType;
import com.hozgan.smartpay.common.model.enums.JournalStatus;
import com.hozgan.smartpay.ledger.entity.AccountBalanceEntity;
import com.hozgan.smartpay.ledger.entity.AccountEntity;
import com.hozgan.smartpay.ledger.entity.JournalEntryEntity;
import com.hozgan.smartpay.ledger.entity.JournalTransactionEntity;
import com.hozgan.smartpay.ledger.repository.AccountBalanceRepository;
import com.hozgan.smartpay.ledger.repository.AccountRepository;
import com.hozgan.smartpay.ledger.repository.JournalEntryRepository;
import com.hozgan.smartpay.ledger.repository.JournalTransactionRepository;
import com.hozgan.smartpay.ledger.service.AccountBalanceService;
import com.hozgan.smartpay.ledger.service.AccountBalanceService.TransferResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Full-stack integration tests for the Ledger Transfer Engine.
 *
 * <p>Uses real PostgreSQL 16 via Testcontainers with Flyway migrations applied.
 * Annotated with {@code @Transactional} so each test method rolls back its state automatically,
 * respecting the PostgreSQL {@code trg_journal_entries_immutable} trigger that strictly prevents
 * any {@code DELETE} on {@code journal_entries}.
 *
 * <p>Covers:
 * <ul>
 *   <li>AC-1: Zero-sum balanced journal posting</li>
 *   <li>AC-4: Hold reservation and capture</li>
 *   <li>AC-5: Idempotency enforcement</li>
 *   <li>Error paths: account not found, insufficient funds, currency mismatch</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("AccountBalanceService — Integration Tests (Testcontainers)")
@TestMethodOrder(MethodOrderer.DisplayName.class)
class LedgerTransferIntegrationTest {

    @Autowired AccountBalanceService accountBalanceService;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountBalanceRepository accountBalanceRepository;
    @Autowired JournalTransactionRepository journalTransactionRepository;
    @Autowired JournalEntryRepository journalEntryRepository;

    private UUID sourceId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        // Account numbers are VARCHAR(32) — use compact random prefixes
        String sSuffix = UUID.randomUUID().toString().substring(0, 8);
        String tSuffix = UUID.randomUUID().toString().substring(0, 8);
        AccountEntity src = new AccountEntity("SRC-" + sSuffix, UUID.randomUUID(), EntityType.SHIPPER, "GBP");
        AccountEntity tgt = new AccountEntity("TGT-" + tSuffix, UUID.randomUUID(), EntityType.CARRIER, "GBP");
        accountRepository.saveAll(List.of(src, tgt));

        sourceId = src.getId();
        targetId = tgt.getId();

        // Seed balances: source £1000, target £0
        accountBalanceRepository.saveAll(List.of(
                new AccountBalanceEntity(sourceId, 100_000L, 0L),   // £1000.00
                new AccountBalanceEntity(targetId,       0L, 0L)    // £0.00
        ));
    }

    // Note: No @AfterEach with deleteAll()!
    // The database trigger trg_journal_entries_immutable strictly prevents DELETE operations.
    // Spring's @Transactional on this test class rolls back every test method automatically.

    // =========================================================================
    // AC-1: Zero-Sum Balanced Journal Posting
    // =========================================================================

    @Test
    @DisplayName("AC-1: transfer £250 posts DEBIT/CREDIT pair and updates balances correctly")
    void ac1_transferPostsBalancedJournal() {
        Money amount = Money.ofGBP("250.00");

        TransferResult result = accountBalanceService.transfer(
                sourceId, targetId, amount,
                "INVOICE_SETTLEMENT", "INV-001",
                "IDEMP-AC1-" + UUID.randomUUID(), "AC-1 test transfer");

        // Transaction header
        assertThat(result.status()).isEqualTo(JournalStatus.POSTED);
        assertThat(result.transactionId()).isNotNull();

        // Balance updates
        assertThat(result.sourceAvailableBalance()).isEqualTo(Money.ofGBP("750.00"));
        assertThat(result.targetAvailableBalance()).isEqualTo(Money.ofGBP("250.00"));

        // Journal entries in DB
        List<JournalEntryEntity> entries = journalEntryRepository
                .findByTransactionId(result.transactionId());
        assertThat(entries).hasSize(2);

        long totalDebit = entries.stream()
                .filter(e -> e.getEntryType().name().equals("DEBIT"))
                .mapToLong(JournalEntryEntity::getAmountInPence).sum();
        long totalCredit = entries.stream()
                .filter(e -> e.getEntryType().name().equals("CREDIT"))
                .mapToLong(JournalEntryEntity::getAmountInPence).sum();

        assertThat(totalDebit).isEqualTo(totalCredit).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("AC-1: source account balance is debited and target account balance is credited")
    void ac1_databaseBalancesMatchAfterTransfer() {
        Money amount = Money.ofGBP("300.00");

        accountBalanceService.transfer(
                sourceId, targetId, amount,
                "FACTORING_PAYOUT", "PAY-001",
                "IDEMP-AC1B-" + UUID.randomUUID(), "Factoring payout");

        AccountBalanceEntity srcBal = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        AccountBalanceEntity tgtBal = accountBalanceRepository.findByAccountId(targetId).orElseThrow();

        assertThat(srcBal.getClearedBalancePence()).isEqualTo(70_000L); // £1000 - £300
        assertThat(tgtBal.getClearedBalancePence()).isEqualTo(30_000L); // £0 + £300
    }

    // =========================================================================
    // AC-4: Hold Reservation & Capture
    // =========================================================================

    @Test
    @DisplayName("AC-4a: holdFunds increases hold_balance and reduces available_balance")
    void ac4a_holdFundsReducesAvailableBalance() {
        Money holdAmount = Money.ofGBP("400.00");

        AccountBalanceEntity afterHold = accountBalanceService.holdFunds(sourceId, holdAmount);

        assertThat(afterHold.getHoldBalancePence()).isEqualTo(40_000L);
        assertThat(afterHold.getAvailableBalancePence()).isEqualTo(60_000L); // £1000 - £400
        assertThat(afterHold.getClearedBalancePence()).isEqualTo(100_000L);   // unchanged
    }

    @Test
    @DisplayName("AC-4b: releaseHold(capture=true) decrements cleared balance and posts journal")
    void ac4b_releaseHoldWithCapturePersistsJournal() {
        Money holdAmount = Money.ofGBP("400.00");
        accountBalanceService.holdFunds(sourceId, holdAmount);

        String idempotencyKey = "HOLD-CAPTURE-" + UUID.randomUUID();
        accountBalanceService.releaseHold(
                sourceId, targetId, holdAmount,
                true, "HOLD_CAPTURE", "HC-001", idempotencyKey);

        AccountBalanceEntity srcBal = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        assertThat(srcBal.getHoldBalancePence()).isEqualTo(0L);
        assertThat(srcBal.getClearedBalancePence()).isEqualTo(60_000L); // £1000 - £400

        // Journal was posted for this idempotency key
        assertThat(journalTransactionRepository.findByIdempotencyKey(idempotencyKey))
                .isPresent()
                .hasValueSatisfying(tx -> assertThat(tx.getStatus()).isEqualTo(JournalStatus.POSTED));
    }

    @Test
    @DisplayName("AC-4c: releaseHold(capture=false) restores available balance without journal")
    void ac4c_releaseHoldWithoutCaptureNoJournal() {
        Money holdAmount = Money.ofGBP("200.00");
        accountBalanceService.holdFunds(sourceId, holdAmount);

        String cancelKey = "IDEMP-CANCEL-" + UUID.randomUUID();
        accountBalanceService.releaseHold(
                sourceId, targetId, holdAmount,
                false, "HOLD_CANCEL", "HC-CANCEL-001", cancelKey);

        AccountBalanceEntity srcBal = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        assertThat(srcBal.getHoldBalancePence()).isEqualTo(0L);
        assertThat(srcBal.getClearedBalancePence()).isEqualTo(100_000L); // unchanged

        // No journal was recorded for the cancelled hold
        assertThat(journalTransactionRepository.findByIdempotencyKey(cancelKey)).isEmpty();
    }

    // =========================================================================
    // AC-5: Idempotency Enforcement
    // =========================================================================

    @Test
    @DisplayName("AC-5: second call with same idempotency key returns cached result without double-debit")
    void ac5_idempotentTransferDoesNotDebitTwice() {
        String idempotencyKey = "IDEMP-UNIQUE-" + UUID.randomUUID();
        Money amount = Money.ofGBP("100.00");

        // First call
        TransferResult first = accountBalanceService.transfer(
                sourceId, targetId, amount,
                "INVOICE_SETTLEMENT", "INV-IDEMP-001",
                idempotencyKey, "First call");

        // Second call — same key
        TransferResult second = accountBalanceService.transfer(
                sourceId, targetId, amount,
                "INVOICE_SETTLEMENT", "INV-IDEMP-001",
                idempotencyKey, "Second call (retry)");

        // Same transaction ID returned
        assertThat(second.transactionId()).isEqualTo(first.transactionId());

        // Exactly ONE transaction in DB with this idempotency key
        assertThat(journalTransactionRepository.findByIdempotencyKey(idempotencyKey)).isPresent();

        // Source balance debited only once: £1000 - £100 = £900
        AccountBalanceEntity srcBal = accountBalanceRepository.findByAccountId(sourceId).orElseThrow();
        assertThat(srcBal.getClearedBalancePence()).isEqualTo(90_000L);
    }

    // =========================================================================
    // Error Paths
    // =========================================================================

    @Test
    @DisplayName("ERR: transfer fails with InsufficientFundsException when available balance < amount")
    void error_insufficientFundsThrowsException() {
        Money amount = Money.ofGBP("1500.00"); // more than £1000 available

        assertThatThrownBy(() -> accountBalanceService.transfer(
                sourceId, targetId, amount,
                "INVOICE_SETTLEMENT", "INV-ERR-001",
                "IDEMP-ERR-" + UUID.randomUUID(), "Should fail"))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(ex -> assertThat(((InsufficientFundsException) ex).errorCode())
                        .isEqualTo("ERR_INSUFFICIENT_FUNDS"))
                .hasMessageContaining("1500.00")
                .hasMessageContaining("1000.00");
    }

    @Test
    @DisplayName("ERR: transfer fails with AccountNotFoundException for unknown account")
    void error_unknownAccountThrowsException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> accountBalanceService.transfer(
                unknownId, targetId, Money.ofGBP("10.00"),
                "INVOICE_SETTLEMENT", "INV-ERR-002",
                "IDEMP-ERR-" + UUID.randomUUID(), "Should fail"))
                .isInstanceOf(AccountNotFoundException.class)
                .satisfies(ex -> assertThat(((AccountNotFoundException) ex).errorCode())
                        .isEqualTo("ERR_ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("ERR: holdFunds fails with InsufficientFundsException when hold exceeds available")
    void error_holdExceedingAvailableBalanceFails() {
        Money holdAmount = Money.ofGBP("1500.00"); // exceeds £1000 cleared

        assertThatThrownBy(() -> accountBalanceService.holdFunds(sourceId, holdAmount))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(ex -> assertThat(((InsufficientFundsException) ex).errorCode())
                        .isEqualTo("ERR_INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("ERR: transfer fails with CurrencyMismatchException for EUR->GBP accounts")
    void error_currencyMismatchThrowsException() {
        // Create a EUR account
        AccountEntity eurAccount = new AccountEntity("EUR-" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID(), EntityType.CARRIER, "EUR");
        accountRepository.save(eurAccount);
        accountBalanceRepository.save(new AccountBalanceEntity(eurAccount.getId(), 50_000L, 0L));

        // Try to transfer GBP amount to a EUR account
        assertThatThrownBy(() -> accountBalanceService.transfer(
                sourceId, eurAccount.getId(), Money.ofGBP("100.00"),
                "INVOICE_SETTLEMENT", "INV-EUR-001",
                "IDEMP-EUR-" + UUID.randomUUID(), "Currency mismatch test"))
                .isInstanceOf(CurrencyMismatchException.class)
                .satisfies(ex -> assertThat(((CurrencyMismatchException) ex).errorCode())
                        .isEqualTo("ERR_CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("ERR: negative balance is impossible — DB constraint rejects it")
    void error_clearedBalanceCannotGoBelowZero() {
        // Seed a balance that is already at £0 + £0 hold
        accountBalanceRepository.save(new AccountBalanceEntity(sourceId, 0L, 0L));

        assertThatThrownBy(() -> accountBalanceService.transfer(
                sourceId, targetId, Money.ofGBP("0.01"),
                "INVOICE_SETTLEMENT", "INV-NEG-001",
                "IDEMP-NEG-" + UUID.randomUUID(), "Should fail"))
                .isInstanceOf(InsufficientFundsException.class);
    }
}
