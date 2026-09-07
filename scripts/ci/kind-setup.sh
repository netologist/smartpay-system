#!/usr/bin/env bash
# ==============================================================================
# SmartPay KinD End-to-End Cluster Provisioning & Orchestration Suite
# Fully bootstraps: KinD -> Ingress -> Infra -> Migrations -> Apps -> E2E Tests
# ==============================================================================

set -euo pipefail

CLUSTER_NAME="${1:-smartpay-cluster}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "============================================================"
echo "☸️  SmartPay Cloud-Native KinD E2E Bootstrap & Test Runner"
echo "   Cluster: ${CLUSTER_NAME}"
echo "============================================================"

# 1. Verify Prerequisites
for tool in kind kubectl docker jq; do
  if ! command -v "${tool}" &>/dev/null; then
    echo "❌ Error: Required tool '${tool}' is not installed."
    exit 1
  fi
done

# 2. Provision KinD Cluster if not already running
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  echo "ℹ️  KinD cluster '${CLUSTER_NAME}' is already running."
else
  echo "🚀 [1/7] Creating KinD cluster with ingress port mappings (80/443)..."
  kind create cluster --config "${REPO_ROOT}/scripts/ci/kind-config.yaml" --name "${CLUSTER_NAME}"
fi

# 3. Deploy NGINX Ingress Controller for KinD
echo "🌐 [2/7] Installing ingress-nginx controller for KinD..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
echo "   Waiting for ingress controller readiness..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

# 4. Deploy Core Infrastructure (PostgreSQL 16 & Redpanda)
echo "🗄️  [3/7] Deploying platform infrastructure in namespace 'smartpay'..."
kubectl create namespace smartpay --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "${REPO_ROOT}/scripts/ci/kind-infra-postgres.yaml"
echo "   Waiting for PostgreSQL to be ready..."
if ! kubectl wait --namespace smartpay --for=condition=available deployment/postgres --timeout=180s; then
  echo "❌ PostgreSQL failed to become ready! Pod diagnostics:"
  kubectl get pods -n smartpay -o wide || true
  kubectl describe pod -l app=postgres -n smartpay || true
  kubectl logs -l app=postgres -n smartpay --tail=100 || true
  exit 1
fi
echo "   ✅ PostgreSQL is ready."

kubectl apply -f "${REPO_ROOT}/scripts/ci/kind-infra-redpanda.yaml"
echo "   Waiting for Redpanda broker to be ready..."
if ! kubectl wait --namespace smartpay --for=condition=available deployment/redpanda --timeout=180s; then
  echo "❌ Redpanda failed to become ready! Pod diagnostics:"
  kubectl describe pod -l app=redpanda -n smartpay || true
  kubectl logs -l app=redpanda -n smartpay --tail=100 || true
  exit 1
fi
echo "   ✅ Redpanda broker is ready."
# 5. Synchronize ConfigMaps & Execute Flyway DB Migrations (Sync Wave 1)
echo "🔄 [4/7] Synchronizing Flyway migration ConfigMaps and executing migration jobs..."
bash "${REPO_ROOT}/scripts/ci/sync-flyway-configmaps.sh"

kubectl apply -k "${REPO_ROOT}/k8s/migrations"
echo "   Waiting for Flyway migration jobs to complete successfully..."
kubectl wait --namespace smartpay --for=condition=complete job --all --timeout=120s
kubectl get jobs -n smartpay

# 6. Build and Load Container Images into KinD
echo "📦 [5/7] Building and loading distroless microservice images into KinD..."
bash "${REPO_ROOT}/scripts/ci/build-and-load-kind.sh" "${CLUSTER_NAME}"

# 7. Deploy Application Microservices via Kustomize (Dev Overlay)
echo "🚢 [6/7] Deploying microservices via Kustomize dev overlay..."
kubectl apply -k "${REPO_ROOT}/k8s/overlays/dev"

echo "   Waiting for payment-service and gateway deployment readiness..."
kubectl wait --namespace smartpay --for=condition=available deployment/smartpay-payment-service --timeout=120s
kubectl wait --namespace smartpay --for=condition=available deployment/smartpay-gateway --timeout=120s
kubectl get deployments -n smartpay

# 8. Run Automated E2E Smoke Test Suite
echo "🧪 [7/7] Executing automated E2E smoke tests..."
kubectl port-forward svc/smartpay-payment-service -n smartpay 8082:8082 >/dev/null 2>&1 &
PF_PAYMENT_PID=$!

kubectl port-forward svc/smartpay-notification-service -n smartpay 8087:8087 >/dev/null 2>&1 &
PF_NOTIF_PID=$!

cleanup() {
  kill -9 "${PF_PAYMENT_PID}" "${PF_NOTIF_PID}" 2>/dev/null || true
}
trap cleanup EXIT

sleep 5
chmod +x "${REPO_ROOT}/scripts/ci/e2e-smoke-test.sh"
BASE_URL="http://localhost:8082" NOTIFICATION_URL="http://localhost:8087" "${REPO_ROOT}/scripts/ci/e2e-smoke-test.sh"

echo "============================================================"
echo "🎉 KIND E2E DEPLOYMENT & TESTING COMPLETED SUCCESSFULLY!"
echo "============================================================"
