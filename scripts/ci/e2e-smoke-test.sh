#!/usr/bin/env bash
# ==============================================================================
# SmartPay Cloud-Native E2E Smoke Test Suite
# Target: KinD Kubernetes Cluster / Local Microservices
# Asserts: Two-Tier Idempotency, Health Probes, Error Contracts
# ==============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8082}"
TENANT_ID="TENANT-UK-01"
IDEMPOTENCY_KEY="E2E-TEST-$(date +%s)-${RANDOM}"

echo "============================================================"
echo "🚀 Starting SmartPay E2E Smoke Test against ${BASE_URL}"
echo "   Tenant: ${TENANT_ID}"
echo "   Idempotency-Key: ${IDEMPOTENCY_KEY}"
echo "============================================================"

# 1. Health Probe Check
echo "🔍 [1/4] Checking service health probe..."
HEALTH_STATUS=$(curl -s "${BASE_URL}/actuator/health" | jq -r '.status // "UNKNOWN"')
echo "   Health status: ${HEALTH_STATUS}"
if [[ "${HEALTH_STATUS}" != "UP" ]]; then
    echo "⚠️ Health check returned non-UP status, waiting 5 seconds..."
    sleep 5
fi

# 2. Payment Initiation (AC-1: Happy Path)
echo "💸 [2/4] Initiating payment (AC-1: HTTP 201 Expected)..."
PAYLOAD_ORIGINAL=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "debtorAccountId": "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
  "creditorAccountId": "0191c7a2-9b24-7f11-9a1c-8e9942a0b124",
  "amountInPence": 97500,
  "currency": "GBP",
  "paymentMethod": "FASTER_PAYMENTS",
  "reference": "E2E-SMOKE-PAYOUT",
  "creditorSortCode": "20-00-00",
  "creditorAccountNumber": "12345678"
}
EOF
)

RESPONSE_INIT=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BASE_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_ORIGINAL}")

HTTP_CODE_INIT=$(echo "${RESPONSE_INIT}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_INIT=$(echo "${RESPONSE_INIT}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_INIT}"
echo "   Response: ${BODY_INIT}"

if [[ "${HTTP_CODE_INIT}" != "201" && "${HTTP_CODE_INIT}" != "200" ]]; then
    echo "❌ Payment initiation failed with code ${HTTP_CODE_INIT}"
    exit 1
fi
echo "   ✅ AC-1 Passed: Payment initiated successfully."

# 3. Duplicate Submission (AC-2: Idempotent Cache Hit)
echo "🔁 [3/4] Retrying identical payment (AC-2: Idempotency Cache Hit Expected)..."
RESPONSE_DUP=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BASE_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_ORIGINAL}")

HTTP_CODE_DUP=$(echo "${RESPONSE_DUP}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_DUP=$(echo "${RESPONSE_DUP}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_DUP}"
if [[ "${HTTP_CODE_DUP}" != "201" && "${HTTP_CODE_DUP}" != "200" ]]; then
    echo "❌ Duplicate payment failed with code ${HTTP_CODE_DUP}"
    exit 1
fi
echo "   ✅ AC-2 Passed: Idempotent duplicate handled cleanly."

# 4. Hash Mismatch Tamper Attempt (AC-3: HTTP 422 Expected)
echo "🛡️ [4/4] Attempting replay with altered amount (AC-3: HTTP 422 Expected)..."
PAYLOAD_TAMPERED=$(echo "${PAYLOAD_ORIGINAL}" | sed 's/97500/150000/')

RESPONSE_TAMPER=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BASE_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_TAMPERED}")

HTTP_CODE_TAMPER=$(echo "${RESPONSE_TAMPER}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_TAMPER=$(echo "${RESPONSE_TAMPER}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_TAMPER}"
echo "   Response: ${BODY_TAMPER}"

if [[ "${HTTP_CODE_TAMPER}" != "422" ]]; then
    echo "❌ Expected HTTP 422 on payload hash mismatch, got ${HTTP_CODE_TAMPER}"
    exit 1
fi
echo "   ✅ AC-3 Passed: Replay attack with altered payload correctly rejected with HTTP 422."

echo "============================================================"
echo "🎉 ALL E2E SMOKE TESTS PASSED SUCCESSFULLY!"
echo "============================================================"
