#!/bin/bash
# ============================================================================
# AML System - Gitea Actions status helper
# Reads the configured origin remote, queries the Gitea Actions API, and prints
# runner/workflow/latest-run status without exposing credentials.
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REMOTE_URL="${GITEA_REMOTE_URL:-$(git remote get-url origin)}"

if [[ "$REMOTE_URL" != http://* && "$REMOTE_URL" != https://* ]]; then
    echo "Only HTTP(S) Gitea remotes are supported by this helper." >&2
    exit 1
fi

SCHEME="${REMOTE_URL%%://*}"
REST="${REMOTE_URL#*://}"
CREDENTIAL=""

if [[ "$REST" == *"@"* ]]; then
    CREDENTIAL="${REST%%@*}"
    HOST_PATH="${REST#*@}"
else
    HOST_PATH="$REST"
fi

HOST="${HOST_PATH%%/*}"
REPO_PATH="${HOST_PATH#*/}"
REPO_PATH="${REPO_PATH%.git}"
OWNER="${GITEA_OWNER:-${REPO_PATH%%/*}}"
REPO="${GITEA_REPO:-${REPO_PATH#*/}}"
API_BASE="${GITEA_API_URL:-${SCHEME}://${HOST}/api/v1}"

CURL_AUTH=()
if [ -n "${GITEA_TOKEN:-}" ]; then
    CURL_AUTH=(-H "Authorization: token ${GITEA_TOKEN}")
elif [ -n "$CREDENTIAL" ]; then
    USERNAME="${CREDENTIAL%%:*}"
    PASSWORD="${CREDENTIAL#*:}"
    CURL_AUTH=(-u "${USERNAME}:${PASSWORD}")
fi

api_get() {
    curl -fsS "${CURL_AUTH[@]}" "$API_BASE$1"
}

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required. Install jq or run from a machine that has jq available." >&2
    exit 1
fi

echo "=========================================="
echo "  AML Gitea Actions Status"
echo "=========================================="
echo "  Repository: ${OWNER}/${REPO}"
echo "  Gitea API: ${API_BASE}"
echo ""

repo_json="$(api_get "/repos/${OWNER}/${REPO}")"
echo "Repository actions enabled: $(printf '%s' "$repo_json" | jq -r '.has_actions')"

echo ""
echo "Runners:"
runners_json="$(api_get "/repos/${OWNER}/${REPO}/actions/runners")"
runner_count="$(printf '%s' "$runners_json" | jq -r '.total_count')"
if [ "$runner_count" = "0" ]; then
    echo "  - none registered"
else
    printf '%s' "$runners_json" \
        | jq -r '.runners[] | "  - \(.name) [\(.status)] busy=\(.busy) labels=\(.labels | map(if type == "object" then .name else . end) | join(","))"'
fi

echo ""
echo "Workflows:"
workflows_json="$(api_get "/repos/${OWNER}/${REPO}/actions/workflows")"
printf '%s' "$workflows_json" | jq -r '.workflows[] | "  - \(.name) path=\(.path) state=\(.state)"'

echo ""
echo "Latest runs:"
runs_json="$(api_get "/repos/${OWNER}/${REPO}/actions/runs?limit=5")"
printf '%s' "$runs_json" | jq -r '.workflow_runs[] | "  - #\(.run_number) run_id=\(.id) status=\(.status) conclusion=\(.conclusion // "-") sha=\(.head_sha[0:7]) title=\(.display_title)"'

latest_run_id="$(printf '%s' "$runs_json" | jq -r '.workflow_runs[0].id // empty')"
if [ -n "$latest_run_id" ]; then
    echo ""
    echo "Latest run jobs:"
    api_get "/repos/${OWNER}/${REPO}/actions/runs/${latest_run_id}/jobs" \
        | jq -r '.jobs[] | "  - \(.name) [\(.status)] conclusion=\(.conclusion // "-")"'
fi
