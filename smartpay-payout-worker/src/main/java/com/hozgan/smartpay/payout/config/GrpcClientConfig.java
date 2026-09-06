package com.hozgan.smartpay.payout.config;

import com.hozgan.smartpay.proto.payment.PaymentServiceGrpc;
import com.hozgan.smartpay.proto.risk.RiskServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(name = "riskManagedChannel", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "riskManagedChannel")
    public ManagedChannel riskManagedChannel(PayoutWorkerProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.riskGrpcHost(), properties.riskGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RiskServiceGrpc.RiskServiceBlockingStub riskServiceBlockingStub(ManagedChannel riskManagedChannel) {
        return RiskServiceGrpc.newBlockingStub(riskManagedChannel);
    }

    @Bean(name = "paymentManagedChannel", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "paymentManagedChannel")
    public ManagedChannel paymentManagedChannel(PayoutWorkerProperties properties) {
        return ManagedChannelBuilder
                .forAddress(properties.paymentGrpcHost(), properties.paymentGrpcPort())
                .usePlaintext()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub(ManagedChannel paymentManagedChannel) {
        return PaymentServiceGrpc.newBlockingStub(paymentManagedChannel);
    }
}
