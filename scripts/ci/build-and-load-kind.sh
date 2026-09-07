#!/usr/bin/env bash
# ==============================================================================
# Build & Load SmartPay Distroless Container Images into KinD Cluster
# ==============================================================================

set -euo pipefail

CLUSTER_NAME="${1:-smartpay-cluster}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

SERVICES=(
  "smartpay-gateway"
  "smartpay-ledger-service"
  "smartpay-payment-service"
  "smartpay-invoice-service"
  "smartpay-payout-worker"
  "smartpay-recon-service"
  "smartpay-risk-service"
  "smartpay-notification-service"
)

echo "============================================================"
echo "🚀 Building & Loading Microservice Images into KinD Cluster"
echo "   Cluster: ${CLUSTER_NAME}"
echo "============================================================"

# Check if kind CLI is available
if ! command -v kind &>/dev/null; then
  echo "❌ Error: 'kind' command not found. Please install KinD first."
  exit 1
fi

# Ensure fat JARs exist, build if missing
MISSING_JARS=false
for svc in "${SERVICES[@]}"; do
  JAR_PATH=$(find "${REPO_ROOT}/${svc}/target" -maxdepth 1 -name "${svc}-*.jar" ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null | head -n 1)
  if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
    MISSING_JARS=true
    break
  fi
done

if [[ "${MISSING_JARS}" == "true" ]]; then
  echo "📦 Compiling and packaging microservice fat JARs via Maven..."
  cd "${REPO_ROOT}"
  mvn clean package -DskipTests
fi

# Pre-load infrastructure images into KinD
echo "📥 Pre-loading infrastructure images (PostgreSQL 16, Redpanda, Flyway) into KinD..."
docker pull postgres:16-alpine
docker pull redpandadata/redpanda:v24.2.4
docker pull flyway/flyway:10.15.0-alpine
kind load docker-image postgres:16-alpine --name "${CLUSTER_NAME}"
kind load docker-image redpandadata/redpanda:v24.2.4 --name "${CLUSTER_NAME}"
kind load docker-image flyway/flyway:10.15.0-alpine --name "${CLUSTER_NAME}"
echo "✅ Infrastructure images cached in KinD."

# Build each service image and load into KinD
for svc in "${SERVICES[@]}"; do
  JAR_PATH=$(find "${REPO_ROOT}/${svc}/target" -maxdepth 1 -name "${svc}-*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -n 1)
  REL_JAR_PATH="${JAR_PATH#${REPO_ROOT}/}"
  IMAGE_TAG="ghcr.io/hozgan/smartpay/${svc}:latest"

  echo "🔨 Building distroless image for ${svc} (from ${REL_JAR_PATH})..."
  docker build \
    -f "${REPO_ROOT}/docker/Dockerfile.prebuilt" \
    --build-arg JAR_FILE="${REL_JAR_PATH}" \
    -t "${IMAGE_TAG}" \
    "${REPO_ROOT}"

  echo "📥 Loading ${IMAGE_TAG} into KinD cluster '${CLUSTER_NAME}'..."
  kind load docker-image "${IMAGE_TAG}" --name "${CLUSTER_NAME}"
  echo "✅ ${svc} loaded successfully."
done

echo "============================================================"
echo "🎉 ALL 8 MICROSERVICE IMAGES LOADED INTO KIND SUCCESSFULLY!"
echo "============================================================"
