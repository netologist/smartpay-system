# syntax=docker/dockerfile:1.4
# ==============================================================================
# Multi-Stage Distroless Dockerfile for SmartPay Microservices
# Platform: Modern Java 25 with Virtual Threads (Project Loom)
# Framework: Spring Boot 4.1
# Security: Distroless Non-Root (UID 10001, GID 10001), Zero Shell, Zero Package Manager
# ==============================================================================

# --- Stage 1: Build & Custom JRE Generation ---
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

# Install Maven, compile and package microservice
RUN apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn clean package -pl smartpay-proto,smartpay-common,${MODULE_NAME} -am -DskipTests && \
    cp ${MODULE_NAME}/target/*.jar /app.jar

# Build custom minimal Java 25 JRE via jlink (removes unnecessary tools/compilers)
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.jfr,jdk.management,jdk.unsupported,jdk.crypto.cryptoki,jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /opt/jre-25

# --- Stage 2: Distroless Non-Root Runtime ---
FROM gcr.io/distroless/cc-debian12:nonroot AS runtime

WORKDIR /app

# Copy custom Java 25 JRE from builder stage
COPY --from=builder --chown=10001:10001 /opt/jre-25 /opt/jre-25

# Copy compiled microservice fat jar
COPY --from=builder --chown=10001:10001 /app.jar /app/application.jar

# Enforce non-root user (UID 10001, GID 10001)
USER 10001:10001

# Optimized JVM flags for Project Loom (Virtual Threads) and container memory limits
ENV JAVA_TOOL_OPTIONS="\
  -XX:+UseZGC \
  -XX:InitialRAMPercentage=50.0 \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["/opt/jre-25/bin/java", "-jar", "/app/application.jar"]
