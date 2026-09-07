#!/usr/bin/env bash
# ==============================================================================
# SmartPay Kafka / Redpanda Topic & Consumer Group Inspector
# Inspects: Event Topics, Message Payloads, Consumer Groups & Lag
# Works seamlessly with: Kubernetes KinD pod, Docker container, or local rpk
# ==============================================================================

set -euo pipefail

NAMESPACE="${NAMESPACE:-smartpay}"
POD_NAME="${POD_NAME:-}"
BROKERS="${BROKERS:-localhost:9092}"

# Auto-detect execution environment (Kubernetes KinD pod vs local Docker / rpk)
detect_runner() {
  if command -v kubectl &>/dev/null && kubectl get pod -n "${NAMESPACE}" -l app=redpanda -o name 2>/dev/null | grep -q pod; then
    POD_NAME=$(kubectl get pod -n "${NAMESPACE}" -l app=redpanda -o jsonpath='{.items[0].metadata.name}')
    echo "k8s"
  elif command -v rpk &>/dev/null; then
    echo "rpk"
  elif command -v docker &>/dev/null && docker ps --format '{{.Names}}' | grep -q redpanda; then
    echo "docker"
  else
    echo "none"
  fi
}

RUNNER=$(detect_runner)

rpk_exec() {
  case "${RUNNER}" in
    k8s)
      kubectl exec -n "${NAMESPACE}" "${POD_NAME}" -- rpk "$@"
      ;;
    rpk)
      rpk "$@" --brokers "${BROKERS}"
      ;;
    docker)
      docker exec -i redpanda rpk "$@"
      ;;
    *)
      echo "❌ Error: Neither Kubernetes pod 'redpanda', local 'rpk', nor Docker container 'redpanda' found."
      exit 1
      ;;
  esac
}

CMD="${1:-summary}"

case "${CMD}" in
  summary|list)
    echo "============================================================"
    echo "📋 SmartPay Event Broker Topics & Consumer Groups"
    echo "   Environment: ${RUNNER} (Pod: ${POD_NAME:-N/A})"
    echo "============================================================"
    echo ""
    echo "📌 Active Topics:"
    rpk_exec topic list
    echo ""
    echo "👥 Active Consumer Groups:"
    rpk_exec group list
    ;;

  consume)
    TOPIC="${2:-smartpay.events.payment}"
    COUNT="${3:-5}"
    echo "============================================================"
    echo "🔍 Consuming latest ${COUNT} messages from topic: ${TOPIC}"
    echo "============================================================"
    rpk_exec topic consume "${TOPIC}" -n "${COUNT}" --format json
    ;;

  groups|lag)
    echo "============================================================"
    echo "👥 Consumer Group Details & Lag Status"
    echo "============================================================"
    GROUPS=("smartpay-factoring-workers" "smartpay-notification-workers")
    for g in "${GROUPS[@]}"; do
      echo ""
      echo "--- Group: ${g} ---"
      rpk_exec group describe "${g}" || echo "Group ${g} not yet registered."
    done
    ;;

  dlq)
    echo "============================================================"
    echo "💀 Inspecting Dead Letter Queue: smartpay.events.notifications.dlq"
    echo "============================================================"
    rpk_exec topic consume smartpay.events.notifications.dlq -n 5 --format json || echo "No messages in DLQ."
    ;;

  *)
    echo "Usage: $0 [summary|list|consume <topic> <n>|groups|lag|dlq]"
    exit 1
    ;;
esac
