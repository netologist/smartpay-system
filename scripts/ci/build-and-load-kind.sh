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

# ------------------------------------------------------------------------------
# Per-node infrastructure image loading
# ------------------------------------------------------------------------------
# `kind load docker-image` pipes `docker save` output into each node's
# `ctr images import --all-platforms`. For multi-platform images (postgres,
# redpanda, flyway), Docker Desktop's containerd image store exports an OCI
# index that references every platform's blobs while only containing the host
# platform's content; the `--all-platforms` import then fails with
# "ctr: content digest sha256:...: not found". Importing without
# `--all-platforms` makes containerd resolve the node's own platform child
# manifest from the same tar, so loading works on classic and containerd
# image stores alike.
load_infra_image() {
  local image="$1"
  for node in $(kind get nodes --name "${CLUSTER_NAME}" 2>/dev/null); do
    if docker exec "${node}" sh -c 'ctr --namespace=k8s.io images ls -q | sed -e "s#^docker.io/##" -e "s#^library/##"' 2>/dev/null | grep -Fxq "${image}"; then
      echo "   ✅ ${image} already present on ${node}."
      continue
    fi
    echo "   📥 Loading ${image} into ${node}..."
    if ! docker save "${image}" | docker exec -i "${node}" ctr --namespace=k8s.io images import --digests --snapshotter=overlayfs - >/dev/null 2>&1; then
      echo "❌ Error: failed to import ${image} into node ${node}."
      exit 1
    fi
    echo "   ✅ ${image} loaded into ${node}."
  done
}

# Pre-load infrastructure images into KinD
echo "📥 Pre-loading infrastructure images (PostgreSQL 16, Redpanda, Flyway) into KinD..."
docker pull postgres:16-alpine
docker pull redpandadata/redpanda:v24.2.4
docker pull flyway/flyway:10.15.0-alpine
for image in postgres:16-alpine redpandadata/redpanda:v24.2.4 flyway/flyway:10.15.0-alpine; do
  load_infra_image "${image}"
done
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
