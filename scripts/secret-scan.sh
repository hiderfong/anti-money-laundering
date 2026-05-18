#!/bin/bash
# ============================================================================
# AML System - Repository Secret Scan
# Scans tracked files for common credentials and hard-coded local defaults.
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git is required" >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

git ls-files -z \
  ':!:scripts/secret-scan.sh' \
  ':!:scripts/e2e-test.sh' \
  ':!:scripts/frontend-e2e.sh' \
  ':!:scripts/rbac-e2e.sh' \
  ':!:scripts/performance-baseline.sh' \
  ':!:scripts/prepare-rbac-e2e-users.sh' \
  ':!:scripts/seed-e2e-business-data.sh' \
  ':!:scripts/cleanup-e2e-data.sh' \
  ':!:scripts/replace-legacy-mock-data.sh' \
  ':!:scripts/seed-neo4j-graph-data.sh' \
  ':!:src/test/**' \
  ':!:frontend/scripts/frontend-browser-e2e.mjs' \
  ':!:docs/development/**' \
  ':!:docs/use-cases/业务闭环验收矩阵.md' \
  ':!:*.lock' \
  > "$TMP_FILE"

failures=0

scan() {
  local label="$1"
  local regex="$2"
  local output
  output="$(xargs -0 grep -I -n -E -e "$regex" < "$TMP_FILE" || true)"
  if [ -n "$output" ]; then
    failures=$((failures + 1))
    echo ""
    echo "[$label]"
    echo "$output"
  fi
}

scan "GitHub or cloud access token" \
  'github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}'

scan "Private key material" \
  '-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----'

scan "Hard-coded AML development infrastructure credential" \
  'aml_(dev|neo4j)_[[:digit:]]+|minio[a-z]+'

scan "Hard-coded AML development JWT or encryption key" \
  'AmlSystem[[:alnum:]]*Jwt[[:alnum:]]*Secret|AmlEncryptKey[[:alnum:]!@#$%^&*()_+=.-]*'

scan "Human-readable password in non-test file" \
  'Aml@Admin#[0-9]{4}!|admin[0-9]{3}'

if [ "$failures" -gt 0 ]; then
  echo ""
  echo "Secret scan failed with $failures finding group(s)."
  echo "If a value is a deliberate test fixture, move it under an allowlisted test path or pass it via environment variables."
  exit 1
fi

echo "Secret scan passed."
