#!/bin/bash
# ============================================================================
# AML System - CI E2E artifact collector
# Builds a downloadable E2E evidence bundle for GitHub/Gitea Actions.
# ============================================================================

set -euo pipefail

ARTIFACT_DIR="${E2E_ARTIFACT_DIR:-/tmp/aml-e2e-artifacts}"
LOG_DIR="${ARTIFACT_DIR}/logs"
STATUS_DIR="${ARTIFACT_DIR}/status"
SCREENSHOT_SOURCE="${SCREENSHOT_DIR:-/tmp/aml-frontend-browser-e2e}"
SCREENSHOT_DIR_OUT="${ARTIFACT_DIR}/screenshots"
SUMMARY_FILE="${ARTIFACT_DIR}/e2e-summary.md"
MANIFEST_FILE="${ARTIFACT_DIR}/manifest.txt"

mkdir -p "$ARTIFACT_DIR" "$LOG_DIR" "$STATUS_DIR" "$SCREENSHOT_DIR_OUT"

copy_if_exists() {
    local source="$1"
    local target="$2"
    if [ -f "$source" ]; then
        cp "$source" "$target"
    fi
}

copy_if_exists /tmp/aml-backend.log "${LOG_DIR}/backend.log"
copy_if_exists /tmp/aml-frontend.log "${LOG_DIR}/frontend.log"
copy_if_exists /tmp/aml-health.json "${ARTIFACT_DIR}/health.json"

if [ -d "$SCREENSHOT_SOURCE" ]; then
    find "$SCREENSHOT_SOURCE" -maxdepth 2 -type f -print0 | while IFS= read -r -d '' file; do
        cp "$file" "$SCREENSHOT_DIR_OUT/"
    done
fi

generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
run_id="${E2E_RUN_ID:-unknown}"
commit_sha="${GITHUB_SHA:-${CI_COMMIT_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}}"
repository="${GITHUB_REPOSITORY:-${GITEA_REPOSITORY:-unknown}}"
workflow="${GITHUB_WORKFLOW:-unknown}"
run_number="${GITHUB_RUN_NUMBER:-unknown}"
run_attempt="${GITHUB_RUN_ATTEMPT:-unknown}"

{
    echo "# AML E2E CI Report"
    echo ""
    echo "| Field | Value |"
    echo "|-------|-------|"
    echo "| Generated at | \`${generated_at}\` |"
    echo "| Repository | \`${repository}\` |"
    echo "| Workflow | \`${workflow}\` |"
    echo "| Run number | \`${run_number}\` |"
    echo "| Run attempt | \`${run_attempt}\` |"
    echo "| Commit | \`${commit_sha}\` |"
    echo "| E2E_RUN_ID | \`${run_id}\` |"
    echo "| API_URL | \`${API_URL:-${BASE_URL:-unknown}}\` |"
    echo "| FRONTEND_URL | \`${FRONTEND_URL:-unknown}\` |"
    echo ""
    echo "## Step Results"
    echo ""
    echo "| Step | Result | Exit Code | Started | Ended | Log |"
    echo "|------|--------|-----------|---------|-------|-----|"

    if find "$STATUS_DIR" -maxdepth 1 -name '*.env' -print -quit | grep -q .; then
        while IFS= read -r status_file; do
            step_name=""
            result=""
            exit_code=""
            started_at=""
            ended_at=""
            log_file=""
            while IFS='=' read -r key value; do
                case "$key" in
                    STEP_NAME) step_name="$value" ;;
                    RESULT) result="$value" ;;
                    EXIT_CODE) exit_code="$value" ;;
                    STARTED_AT) started_at="$value" ;;
                    ENDED_AT) ended_at="$value" ;;
                    LOG_FILE) log_file="$value" ;;
                esac
            done < "$status_file"
            echo "| ${step_name:-unknown} | ${result:-UNKNOWN} | ${exit_code:-unknown} | \`${started_at:-unknown}\` | \`${ended_at:-unknown}\` | \`${log_file:-unknown}\` |"
        done < <(find "$STATUS_DIR" -maxdepth 1 -name '*.env' | sort)
    else
        echo "| No wrapped E2E steps found | UNKNOWN | unknown | unknown | unknown | logs unavailable |"
    fi

    echo ""
    echo "## Service Logs"
    echo ""
    if [ -f "${LOG_DIR}/backend.log" ]; then
        echo "- \`logs/backend.log\`"
    else
        echo "- backend log unavailable"
    fi
    if [ -f "${LOG_DIR}/frontend.log" ]; then
        echo "- \`logs/frontend.log\`"
    else
        echo "- frontend log unavailable"
    fi

    echo ""
    echo "## Screenshots"
    echo ""
    if find "$SCREENSHOT_DIR_OUT" -maxdepth 1 -type f -print -quit | grep -q .; then
        find "$SCREENSHOT_DIR_OUT" -maxdepth 1 -type f | sort | while IFS= read -r screenshot; do
            echo "- \`screenshots/$(basename "$screenshot")\`"
        done
    else
        echo "- No screenshots found."
    fi
} > "$SUMMARY_FILE"

find "$ARTIFACT_DIR" -type f | sort > "$MANIFEST_FILE"

echo "E2E artifact bundle prepared at ${ARTIFACT_DIR}"
echo "Summary: ${SUMMARY_FILE}"
echo ""
cat "$SUMMARY_FILE"
