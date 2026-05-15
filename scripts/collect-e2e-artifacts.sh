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
HTML_REPORT_FILE="${ARTIFACT_DIR}/e2e-summary.html"
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

html_escape() {
    printf '%s' "$1" | sed \
        -e 's/&/\&amp;/g' \
        -e 's/</\&lt;/g' \
        -e 's/>/\&gt;/g' \
        -e 's/"/\&quot;/g'
}

status_class() {
    case "$1" in
        PASS|SUCCESS|success) echo "pass" ;;
        FAIL|FAILED|failure|ERROR) echo "fail" ;;
        *) echo "unknown" ;;
    esac
}

{
    cat <<HTML
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>AML E2E CI Report</title>
  <style>
    :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #f6f8fb; color: #18212f; }
    main { max-width: 1120px; margin: 0 auto; padding: 32px 24px 48px; }
    h1 { margin: 0 0 8px; font-size: 28px; }
    h2 { margin: 32px 0 12px; font-size: 20px; }
    .meta, .card { background: #fff; border: 1px solid #dbe3ef; border-radius: 8px; padding: 16px; box-shadow: 0 1px 2px rgba(15, 23, 42, .04); }
    table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #dbe3ef; border-radius: 8px; overflow: hidden; }
    th, td { padding: 10px 12px; border-bottom: 1px solid #e6edf5; text-align: left; font-size: 14px; }
    th { background: #edf3fb; color: #26364d; }
    tr:last-child td { border-bottom: 0; }
    code { background: #eef3f9; padding: 2px 5px; border-radius: 4px; }
    .badge { display: inline-block; min-width: 72px; padding: 3px 8px; border-radius: 999px; font-weight: 700; text-align: center; }
    .pass { color: #065f46; background: #d1fae5; }
    .fail { color: #991b1b; background: #fee2e2; }
    .unknown { color: #92400e; background: #fef3c7; }
    .shots { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
    .shot img { width: 100%; border: 1px solid #dbe3ef; border-radius: 6px; background: #fff; }
    .muted { color: #64748b; }
  </style>
</head>
<body>
<main>
  <h1>AML E2E CI Report</h1>
  <p class="muted">Generated at <code>$(html_escape "$generated_at")</code></p>
  <section class="meta">
    <table>
      <tr><th>Field</th><th>Value</th></tr>
      <tr><td>Repository</td><td><code>$(html_escape "$repository")</code></td></tr>
      <tr><td>Workflow</td><td><code>$(html_escape "$workflow")</code></td></tr>
      <tr><td>Run number</td><td><code>$(html_escape "$run_number")</code></td></tr>
      <tr><td>Run attempt</td><td><code>$(html_escape "$run_attempt")</code></td></tr>
      <tr><td>Commit</td><td><code>$(html_escape "$commit_sha")</code></td></tr>
      <tr><td>E2E_RUN_ID</td><td><code>$(html_escape "$run_id")</code></td></tr>
      <tr><td>API_URL</td><td><code>$(html_escape "${API_URL:-${BASE_URL:-unknown}}")</code></td></tr>
      <tr><td>FRONTEND_URL</td><td><code>$(html_escape "${FRONTEND_URL:-unknown}")</code></td></tr>
    </table>
  </section>
  <h2>Step Results</h2>
  <table>
    <tr><th>Step</th><th>Result</th><th>Exit Code</th><th>Started</th><th>Ended</th><th>Log</th></tr>
HTML

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
            css_class="$(status_class "${result:-UNKNOWN}")"
            echo "    <tr><td>$(html_escape "${step_name:-unknown}")</td><td><span class=\"badge ${css_class}\">$(html_escape "${result:-UNKNOWN}")</span></td><td>$(html_escape "${exit_code:-unknown}")</td><td><code>$(html_escape "${started_at:-unknown}")</code></td><td><code>$(html_escape "${ended_at:-unknown}")</code></td><td><code>$(html_escape "${log_file:-unknown}")</code></td></tr>"
        done < <(find "$STATUS_DIR" -maxdepth 1 -name '*.env' | sort)
    else
        echo "    <tr><td>No wrapped E2E steps found</td><td><span class=\"badge unknown\">UNKNOWN</span></td><td>unknown</td><td>unknown</td><td>unknown</td><td>logs unavailable</td></tr>"
    fi

    cat <<HTML
  </table>
  <h2>Service Logs</h2>
  <section class="card">
HTML
    if [ -f "${LOG_DIR}/backend.log" ]; then
        echo "    <p><a href=\"logs/backend.log\">logs/backend.log</a></p>"
    else
        echo "    <p class=\"muted\">backend log unavailable</p>"
    fi
    if [ -f "${LOG_DIR}/frontend.log" ]; then
        echo "    <p><a href=\"logs/frontend.log\">logs/frontend.log</a></p>"
    else
        echo "    <p class=\"muted\">frontend log unavailable</p>"
    fi

    cat <<HTML
  </section>
  <h2>Screenshots</h2>
  <section class="shots">
HTML
    if find "$SCREENSHOT_DIR_OUT" -maxdepth 1 -type f -print -quit | grep -q .; then
        find "$SCREENSHOT_DIR_OUT" -maxdepth 1 -type f | sort | while IFS= read -r screenshot; do
            name="$(basename "$screenshot")"
            echo "    <article class=\"shot\"><a href=\"screenshots/$(html_escape "$name")\"><img src=\"screenshots/$(html_escape "$name")\" alt=\"$(html_escape "$name")\"></a><p><code>$(html_escape "$name")</code></p></article>"
        done
    else
        echo "    <p class=\"muted\">No screenshots found.</p>"
    fi
    cat <<HTML
  </section>
</main>
</body>
</html>
HTML
} > "$HTML_REPORT_FILE"

find "$ARTIFACT_DIR" -type f | sort > "$MANIFEST_FILE"

echo "E2E artifact bundle prepared at ${ARTIFACT_DIR}"
echo "Summary: ${SUMMARY_FILE}"
echo "HTML report: ${HTML_REPORT_FILE}"
echo ""
cat "$SUMMARY_FILE"
