package com.hozgan.smartpay.risk.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the gRPC Netty server lifecycle within the Spring Boot ApplicationContext.
 * Employs Java 25 Virtual Threads for zero-overhead non-blocking I/O.
 */
@Component
@Slf4j
public class GrpcServerLifecycle implements SmartLifecycle {

    private final boolean enabled;
    private final int configuredPort;
    private final RiskGrpcService riskGrpcService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Server server;

    public GrpcServerLifecycle(
            @Value("${grpc.server.enabled:true}") boolean enabled,
            @Value("${grpc.server.port:9091}") int configuredPort,
            RiskGrpcService riskGrpcService) {
        this.enabled = enabled;
        this.configuredPort = configuredPort;
        this.riskGrpcService = riskGrpcService;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("gRPC Server is disabled by configuration (grpc.server.enabled=false)");
            return;
        }

        if (running.compareAndSet(false, true)) {
            try {
                this.server = ServerBuilder.forPort(configuredPort)
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .addService(riskGrpcService)
                        .addService(ProtoReflectionService.newInstance())
                        .build()
                        .start();

                log.info("gRPC Netty Server started on port {} using Java 25 Virtual Threads", server.getPort());
            } catch (IOException e) {
                running.set(false);
                log.error("Failed to start gRPC Server on port {}", configuredPort, e);
                throw new IllegalStateException("Could not start gRPC server on port " + configuredPort, e);
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false) && server != null) {
            log.info("Shutting down gRPC Server on port {}...", server.getPort());
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("gRPC Server did not terminate gracefully within 5 seconds; forcing shutdown");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("gRPC Server stopped successfully");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && server != null && !server.isShutdown();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public int getPort() {
        return server != null ? server.getPort() : configuredPort;
    }
}
