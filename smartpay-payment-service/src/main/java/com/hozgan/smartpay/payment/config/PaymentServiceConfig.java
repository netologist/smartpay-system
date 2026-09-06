package com.hozgan.smartpay.payment.config;

import com.hozgan.smartpay.proto.ledger.LedgerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PaymentServiceConfig {

    @Value("${smartpay.payment.ledger-grpc-host:localhost}")
    private String ledgerGrpcHost;

    @Value("${smartpay.payment.ledger-grpc-port:9090}")
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
