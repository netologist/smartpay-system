package com.hozgan.smartpay.recon.config;

import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the gRPC managed channel and blocking stub used to query the Ledger service
 * during bank statement reconciliation.
 */
@Configuration
public class ReconServiceConfig {

    @Value("${smartpay.recon.ledger-grpc-host:localhost}")
    private String ledgerGrpcHost;

    @Value("${smartpay.recon.ledger-grpc-port:9090}")
    private int ledgerGrpcPort;

    @Bean
    public ManagedChannel ledgerManagedChannel() {
        return ManagedChannelBuilder
                .forAddress(ledgerGrpcHost, ledgerGrpcPort)
                .usePlaintext()
                .build();
    }

    @Bean
    public LedgerServiceGrpc.LedgerServiceBlockingStub ledgerBlockingStub(ManagedChannel ledgerManagedChannel) {
        return LedgerServiceGrpc.newBlockingStub(ledgerManagedChannel);
    }
}
