package com.hozgan.smartpay.recon.service;

import com.hozgan.smartpay.common.exception.LedgerQueryException;
import com.hozgan.smartpay.common.model.Money;
import com.hozgan.smartpay.common.model.enums.EntryType;
import com.hozgan.smartpay.proto.ledger.GetTransactionByReferenceRequest;
import com.hozgan.smartpay.proto.ledger.GetTransactionByReferenceResponse;
import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * gRPC client for querying ledger transactions by their end-to-end reference during reconciliation.
 * Translates gRPC statuses into typed domain results that the {@link ReconciliationEngine} can act on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerReconGrpcClient {

    private final LedgerServiceGrpc.LedgerServiceBlockingStub ledgerStub;

    /**
     * Queries the ledger for a posted journal transaction identified by its end-to-end reference.
     *
     * @param endToEndId the end-to-end reference to look up
     * @return the matching transaction detail, or {@link Optional#empty()} if not found (gRPC NOT_FOUND)
     * @throws ReconciliationException if the ledger returns an unexpected gRPC error
     */
    public Optional<LedgerTransactionDetail> findByEndToEndId(String endToEndId) {
        GetTransactionByReferenceRequest request = GetTransactionByReferenceRequest.newBuilder()
                .setEndToEndId(endToEndId)
                .build();
        try {
            GetTransactionByReferenceResponse response = ledgerStub.getTransactionByReference(request);
            Money amount = ReconGrpcMapper.toMoney(response.getAmount());
            EntryType entryType = switch (response.getEntryType()) {
                case DEBIT -> EntryType.DEBIT;
                case CREDIT -> EntryType.CREDIT;
                default -> throw new LedgerQueryException(
                        "ERR_LEDGER_UNKNOWN_ENTRY_TYPE",
                        "Ledger returned unknown entry type for endToEndId: " + endToEndId);
            };
            return Optional.of(new LedgerTransactionDetail(
                    response.getTransactionId(),
                    response.getEndToEndId(),
                    amount,
                    entryType,
                    response.getCurrency()
            ));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.debug("Ledger has no posted transaction for endToEndId '{}'", endToEndId);
                return Optional.empty();
            }
            log.error("Ledger gRPC error querying endToEndId '{}': {}", endToEndId, e.getStatus());
            throw new LedgerQueryException(
                    "ERR_LEDGER_GRPC",
                    "Ledger gRPC call failed for endToEndId " + endToEndId + ": " + e.getStatus().getDescription());
        }
    }

    /**
     * Immutable view of a ledger transaction returned from the gRPC query.
     */
    public record LedgerTransactionDetail(
            String transactionId,
            String endToEndId,
            Money amount,
            EntryType entryType,
            String currency
    ) {}
}
