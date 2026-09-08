#!/usr/bin/env bash
# ==============================================================================
# SmartPay Cloud-Native E2E Smoke Test Suite
# Target: KinD Kubernetes Cluster (public entry: smartpay-gateway)
# Asserts: Two-Tier Idempotency, Error Contracts, Event Notifications
#
# Architecture rule: external traffic NEVER reaches a service directly.
# Every business call below goes through the edge gateway (GATEWAY_URL);
# internal reachability (actuator probes) is the workflow's job via kubectl.
# ==============================================================================

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TENANT_ID="TENANT-UK-01"
IDEMPOTENCY_KEY="E2E-TEST-$(date +%s)-${RANDOM}"

echo "============================================================"
echo "🚀 Starting SmartPay E2E Smoke Test via gateway ${GATEWAY_URL}"
echo "   Tenant: ${TENANT_ID}"
echo "   Idempotency-Key: ${IDEMPOTENCY_KEY}"
echo "============================================================"

# 1. Gateway Health Probe (public edge)
echo "🔍 [1/5] Checking gateway health probe..."
HEALTH_STATUS=$(curl -s "${GATEWAY_URL}/actuator/health" | jq -r '.status // "UNKNOWN"')
echo "   Health status: ${HEALTH_STATUS}"
if [[ "${HEALTH_STATUS}" != "UP" ]]; then
  echo "❌ Gateway health check failed! Expected UP, got ${HEALTH_STATUS}"
  exit 1
fi
echo "   ✅ Edge gateway is healthy."

# 2. Payment Initiation (AC-1: Happy Path, routed via gateway)
echo "💸 [2/5] Initiating payment (AC-1: HTTP 201 Expected)..."
PAYLOAD_ORIGINAL=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "debtorAccountId": "0191c7a2-9b24-7f11-9a1c-3d842b10a512",
  "creditorAccountId": "0191c7a2-9b24-7f11-9a1c-3d842b10a513",
  "amountInPence": 97500,
  "currency": "GBP",
  "paymentMethod": "FASTER_PAYMENTS",
  "reference": "CARRIER-PAYOUT-LD-889",
  "creditorSortCode": "20-00-00",
  "creditorAccountNumber": "12345678"
}
EOF
)

RESPONSE_INIT=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${GATEWAY_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_ORIGINAL}")

HTTP_CODE_INIT=$(echo "${RESPONSE_INIT}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_INIT=$(echo "${RESPONSE_INIT}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_INIT}"
echo "   Response: ${BODY_INIT}"

if [[ "${HTTP_CODE_INIT}" != "201" && "${HTTP_CODE_INIT}" != "200" ]]; then
  echo "❌ Payment initiation failed! Expected HTTP 201/200, got ${HTTP_CODE_INIT}"
  exit 1
fi
echo "   ✅ AC-1 Passed: Payment initiated successfully."

# 3. Duplicate Submission (AC-2: Idempotent Cache Hit)
echo "🔁 [3/5] Retrying identical payment (AC-2: Idempotency Cache Hit Expected)..."
RESPONSE_DUP=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${GATEWAY_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_ORIGINAL}")

HTTP_CODE_DUP=$(echo "${RESPONSE_DUP}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_DUP=$(echo "${RESPONSE_DUP}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_DUP}"
if [[ "${HTTP_CODE_DUP}" != "201" && "${HTTP_CODE_DUP}" != "200" ]]; then
  echo "❌ Idempotency replay failed! Expected HTTP 201/200, got ${HTTP_CODE_DUP}"
  exit 1
fi
echo "   ✅ AC-2 Passed: Idempotent duplicate handled cleanly."

# 4. Hash Mismatch Tamper Attempt (AC-3: HTTP 422 Expected)
echo "🛡️ [4/5] Attempting replay with altered amount (AC-3: HTTP 422 Expected)..."
PAYLOAD_TAMPERED=$(echo "${PAYLOAD_ORIGINAL}" | sed 's/"amountInPence": 97500/"amountInPence": 150000/')

RESPONSE_TAMPER=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${GATEWAY_URL}/api/v1/payments/initiate" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "${PAYLOAD_TAMPERED}")

HTTP_CODE_TAMPER=$(echo "${RESPONSE_TAMPER}" | grep "HTTP_CODE:" | cut -d':' -f2)
BODY_TAMPER=$(echo "${RESPONSE_TAMPER}" | sed '/HTTP_CODE:/d')

echo "   HTTP Code: ${HTTP_CODE_TAMPER}"
echo "   Response: ${BODY_TAMPER}"

if [[ "${HTTP_CODE_TAMPER}" != "422" ]]; then
  echo "❌ Hash mismatch check failed! Expected HTTP 422, got ${HTTP_CODE_TAMPER}"
  exit 1
fi
echo "   ✅ AC-3 Passed: Replay attack with altered payload correctly rejected with HTTP 422."

# 5. Multi-Channel Notification Dispatch via Gateway (STORY-008, optional)
echo "🔔 [5/5] Testing notification dispatch via gateway..."
NOTIF_EVENT_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
NOTIF_PAYLOAD=$(cat <<EOF
{
  "eventId": "${NOTIF_EVENT_ID}",
  "eventType": "PAYMENT_SETTLED",
  "channel": "SMS",
  "recipient": "+447700900123",
  "templateCode": "PAYMENT_SETTLED",
  "parameters": {
    "formattedAmount": "£975.00",
    "carrierName": "FastFreight Logistics",
    "bankRef": "FP-9912"
  }
}
EOF
)
NOTIF_RESP=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${GATEWAY_URL}/api/v1/notifications/dispatch" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: NOTIF-${IDEMPOTENCY_KEY}" \
  -d "${NOTIF_PAYLOAD}")
NOTIF_CODE=$(echo "${NOTIF_RESP}" | grep "HTTP_CODE:" | cut -d':' -f2)
echo "   Notification HTTP Code: ${NOTIF_CODE}"
if [[ "${NOTIF_CODE}" == "200" || "${NOTIF_CODE}" == "201" ]]; then
  echo "   ✅ Notification dispatch verified successfully."
else
  echo "   ℹ️ Notification dispatch returned ${NOTIF_CODE} (optional check, not failing the suite)."
fi

echo "============================================================"
echo "🎉 ALL E2E SMOKE TESTS PASSED SUCCESSFULLY!"
echo "============================================================"
