#!/bin/bash
# ============================================================================
# AML System - lightweight API performance baseline collector
# Produces CSV and Markdown reports for selected read-heavy endpoints.
# ============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api}"
OUT_DIR="${PERF_ARTIFACT_DIR:-/tmp/aml-performance-baseline}"
ITERATIONS="${ITERATIONS:-20}"
WARMUP="${WARMUP:-2}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-admin123}"
TOKEN="${TOKEN:-}"

mkdir -p "$OUT_DIR"

CSV_FILE="${OUT_DIR}/performance-baseline.csv"
MD_FILE="${OUT_DIR}/performance-baseline.md"

DEFAULT_ENDPOINTS=(
  "system-health|GET|/system/health|false"
  "dashboard-overview|GET|/dashboard/overview|true"
  "customers-page|GET|/customers/page?pageNum=1&pageSize=20|true"
  "transactions-page|GET|/transactions/page?pageNum=1&pageSize=20|true"
  "alerts-page|GET|/alerts/page?pageNum=1&pageSize=20|true"
  "cases-page|GET|/cases/page?pageNum=1&pageSize=20|true"
)

if [ -n "${BASELINE_ENDPOINTS_FILE:-}" ] && [ -f "$BASELINE_ENDPOINTS_FILE" ]; then
  mapfile -t ENDPOINTS < "$BASELINE_ENDPOINTS_FILE"
else
  ENDPOINTS=("${DEFAULT_ENDPOINTS[@]}")
fi

extract_token() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '.data.accessToken // empty'
  else
    sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
  fi
}

login_if_needed() {
  if [ -n "$TOKEN" ]; then
    return
  fi

  local response
  response="$(curl -sS -X POST "${BASE_URL}/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" || true)"

  TOKEN="$(printf '%s' "$response" | extract_token)"
  if [ -z "$TOKEN" ]; then
    echo "WARN: unable to obtain access token; authenticated endpoints may return 401" >&2
  fi
}

percentile_95() {
  local file="$1"
  local count
  count="$(wc -l < "$file" | tr -d ' ')"
  if [ "$count" -eq 0 ]; then
    echo "0"
    return
  fi
  sort -n "$file" | awk -v n="$count" 'BEGIN { idx = int(n * 0.95); if (idx < n * 0.95) idx++; if (idx < 1) idx = 1 } NR == idx { printf "%.2f", $1 }'
}

average() {
  local file="$1"
  awk '{ sum += $1; count++ } END { if (count == 0) printf "0"; else printf "%.2f", sum / count }' "$file"
}

min_value() {
  sort -n "$1" | awk 'NR == 1 { printf "%.2f", $1 }'
}

max_value() {
  sort -n "$1" | awk 'END { if (NR == 0) printf "0"; else printf "%.2f", $1 }'
}

login_if_needed

generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
commit_sha="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"

echo "endpoint,method,path,auth_required,iterations,success_count,last_http,min_ms,avg_ms,p95_ms,max_ms" > "$CSV_FILE"

{
  echo "# AML API Performance Baseline"
  echo ""
  echo "| Field | Value |"
  echo "|-------|-------|"
  echo "| Generated at | \`${generated_at}\` |"
  echo "| Commit | \`${commit_sha}\` |"
  echo "| BASE_URL | \`${BASE_URL}\` |"
  echo "| Iterations | \`${ITERATIONS}\` |"
  echo "| Warmup | \`${WARMUP}\` |"
  echo ""
  echo "## Endpoint Results"
  echo ""
  echo "| Endpoint | Method | Path | Success | Last HTTP | Min ms | Avg ms | P95 ms | Max ms |"
  echo "|----------|--------|------|---------|-----------|--------|--------|--------|--------|"
} > "$MD_FILE"

for endpoint in "${ENDPOINTS[@]}"; do
  IFS='|' read -r name method path auth_required <<< "$endpoint"
  times_file="$(mktemp)"
  success_count=0
  last_http="000"
  total_runs=$((ITERATIONS + WARMUP))

  for ((i = 1; i <= total_runs; i++)); do
    tmp_body="$(mktemp)"
    headers=(-H "Accept: application/json")
    if [ "$auth_required" = "true" ] && [ -n "$TOKEN" ]; then
      headers+=(-H "Authorization: Bearer ${TOKEN}")
    fi

    result="$(curl -sS -o "$tmp_body" -w "%{http_code} %{time_total}" -X "$method" "${headers[@]}" "${BASE_URL}${path}" || echo "000 0")"
    rm -f "$tmp_body"

    last_http="$(printf '%s' "$result" | awk '{ print $1 }')"
    seconds="$(printf '%s' "$result" | awk '{ print $2 }')"
    millis="$(awk -v s="$seconds" 'BEGIN { printf "%.2f", s * 1000 }')"

    if [ "$i" -gt "$WARMUP" ]; then
      echo "$millis" >> "$times_file"
      if [ "$last_http" -ge 200 ] && [ "$last_http" -lt 400 ]; then
        success_count=$((success_count + 1))
      fi
    fi
  done

  min_ms="$(min_value "$times_file")"
  avg_ms="$(average "$times_file")"
  p95_ms="$(percentile_95 "$times_file")"
  max_ms="$(max_value "$times_file")"
  rm -f "$times_file"

  echo "${name},${method},${path},${auth_required},${ITERATIONS},${success_count},${last_http},${min_ms},${avg_ms},${p95_ms},${max_ms}" >> "$CSV_FILE"
  echo "| \`${name}\` | ${method} | \`${path}\` | ${success_count}/${ITERATIONS} | ${last_http} | ${min_ms} | ${avg_ms} | ${p95_ms} | ${max_ms} |" >> "$MD_FILE"
done

echo "Performance baseline written to ${OUT_DIR}"
echo "CSV: ${CSV_FILE}"
echo "Markdown: ${MD_FILE}"
