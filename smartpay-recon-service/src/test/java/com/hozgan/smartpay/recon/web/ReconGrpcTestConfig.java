package com.hozgan.smartpay.recon.web;

import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.ManagedChannel;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real gRPC channel and blocking stub with Mockito mocks for integration tests
 * that do not spin up a Ledger service.
 */
@TestConfiguration
public class ReconGrpcTestConfig {

    @Bean
    @Primary
    public ManagedChannel ledgerManagedChannel() {
        return Mockito.mock(ManagedChannel.class);
    }

    @Bean
    @Primary
    public LedgerServiceGrpc.LedgerServiceBlockingStub ledgerBlockingStub() {
        return Mockito.mock(LedgerServiceGrpc.LedgerServiceBlockingStub.class);
    }
}
