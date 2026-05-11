#!/bin/bash
# ============================================================================
# AML System - CI E2E step wrapper
# Runs one E2E command while capturing a per-step log and status metadata.
# ============================================================================

set -o pipefail

if [ "$#" -lt 2 ]; then
    echo "Usage: scripts/ci-e2e-step.sh <step-name> <command> [args...]" >&2
    exit 64
fi

STEP_NAME="$1"
shift

ARTIFACT_DIR="${E2E_ARTIFACT_DIR:-/tmp/aml-e2e-artifacts}"
LOG_DIR="${ARTIFACT_DIR}/logs"
STATUS_DIR="${ARTIFACT_DIR}/status"
STEP_SLUG="$(printf "%s" "$STEP_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs '[:alnum:]' '-' | sed 's/^-//; s/-$//')"

if [ -z "$STEP_SLUG" ]; then
    STEP_SLUG="e2e-step"
fi

mkdir -p "$LOG_DIR" "$STATUS_DIR"

STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
LOG_FILE="${LOG_DIR}/${STEP_SLUG}.log"
STATUS_FILE="${STATUS_DIR}/${STEP_SLUG}.env"

echo "=========================================="
echo "  CI E2E Step: ${STEP_NAME}"
echo "=========================================="
echo "  Started: ${STARTED_AT}"
echo "  Log: ${LOG_FILE}"
echo ""

set +e
"$@" 2>&1 | tee "$LOG_FILE"
EXIT_CODE="${PIPESTATUS[0]}"
set -e

ENDED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
if [ "$EXIT_CODE" -eq 0 ]; then
    RESULT="PASS"
else
    RESULT="FAIL"
fi

cat > "$STATUS_FILE" <<EOF
STEP_NAME=${STEP_NAME}
STEP_SLUG=${STEP_SLUG}
RESULT=${RESULT}
EXIT_CODE=${EXIT_CODE}
STARTED_AT=${STARTED_AT}
ENDED_AT=${ENDED_AT}
LOG_FILE=logs/${STEP_SLUG}.log
EOF

echo ""
echo "=========================================="
echo "  CI E2E Step Result: ${RESULT}"
echo "=========================================="
echo "  Ended: ${ENDED_AT}"
echo "  Exit code: ${EXIT_CODE}"
echo "  Status: ${STATUS_FILE}"

exit "$EXIT_CODE"
