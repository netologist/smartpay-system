# syntax=docker/dockerfile:1.4
# ==============================================================================
# Multi-Stage Distroless Dockerfile for SmartPay Microservices
# Platform: Modern Java 25 with Virtual Threads (Project Loom)
# Framework: Spring Boot 4.1
# Security: Distroless Non-Root (UID 10001, GID 10001)
# ==============================================================================

# --- Stage 1: Build & Package ---
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

# Cache maven dependencies layer
COPY pom.xml .
COPY smartpay-proto/pom.xml smartpay-proto/
COPY smartpay-common/pom.xml smartpay-common/
COPY smartpay-gateway/pom.xml smartpay-gateway/
COPY smartpay-ledger-service/pom.xml smartpay-ledger-service/
COPY smartpay-invoice-service/pom.xml smartpay-invoice-service/
COPY smartpay-payment-service/pom.xml smartpay-payment-service/
COPY smartpay-payout-worker/pom.xml smartpay-payout-worker/
COPY smartpay-recon-service/pom.xml smartpay-recon-service/
COPY smartpay-risk-service/pom.xml smartpay-risk-service/
COPY smartpay-notification-service/pom.xml smartpay-notification-service/

# Copy source trees
COPY smartpay-proto smartpay-proto/
COPY smartpay-common smartpay-common/
COPY smartpay-gateway smartpay-gateway/
COPY smartpay-ledger-service smartpay-ledger-service/
COPY smartpay-invoice-service smartpay-invoice-service/
COPY smartpay-payment-service smartpay-payment-service/
COPY smartpay-payout-worker smartpay-payout-worker/
COPY smartpay-recon-service smartpay-recon-service/
COPY smartpay-risk-service smartpay-risk-service/
COPY smartpay-notification-service smartpay-notification-service/

# ARG determines which microservice is packaged
ARG MODULE_NAME=smartpay-payment-service

# Install Maven if needed or use distribution
RUN apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn clean package -pl smartpay-proto,smartpay-common,${MODULE_NAME} -am -DskipTests && \
    cp ${MODULE_NAME}/target/*.jar /app.jar

# --- Stage 2: Distroless Runtime ---
FROM eclipse-temurin:25-jre-noble AS runtime

# Run as non-root user (UID 10001)
USER 10001:10001

WORKDIR /app

COPY --from=builder --chown=10001:10001 /app.jar /app/application.jar

# Optimized JVM flags for Project Loom (Virtual Threads) and container memory limits
ENV JAVA_TOOL_OPTIONS="\
  -XX:+UseZGC \
  -XX:InitialRAMPercentage=50.0 \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
