package com.hozgan.smartpay.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ledger service infrastructure configuration.
 *
 * <p>Provides a Virtual Thread executor for CPU-light, I/O-heavy ledger operations.
 * Using {@code Executors.newVirtualThreadPerTaskExecutor()} means each submitted task
 * runs on its own virtual thread — carrier thread pinning is avoided because the
 * service layer uses no {@code synchronized} blocks.
 */
@Configuration
public class LedgerServiceConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
