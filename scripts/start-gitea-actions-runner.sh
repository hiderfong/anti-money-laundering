#!/bin/bash
# ============================================================================
# AML System - Start local Gitea Actions runner
# Starts a Docker-based act_runner registered to the repository configured as
# origin. The registration token is fetched at runtime and is never printed.
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REMOTE_URL="${GITEA_REMOTE_URL:-$(git remote get-url origin)}"

if [[ "$REMOTE_URL" != http://* && "$REMOTE_URL" != https://* ]]; then
    echo "Only HTTP(S) Gitea remotes are supported by this helper." >&2
    exit 1
fi

for command_name in curl docker jq; do
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "$command_name is required but was not found." >&2
        exit 1
    fi
done

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
else
    echo "Set GITEA_TOKEN or include credentials in the HTTP(S) origin remote." >&2
    exit 1
fi

api_get() {
    curl -fsS "${CURL_AUTH[@]}" "$API_BASE$1"
}

RUNNER_HOST="$HOST"
DOCKER_HOST_ARGS=()
if [[ "$HOST" == localhost:* || "$HOST" == 127.0.0.1:* ]]; then
    RUNNER_HOST="host.docker.internal:${HOST#*:}"
    DOCKER_HOST_ARGS=(--add-host host.docker.internal:host-gateway)
fi

INSTANCE_URL="${GITEA_RUNNER_INSTANCE_URL:-${SCHEME}://${RUNNER_HOST}/}"
RUNNER_CONTAINER="${GITEA_RUNNER_CONTAINER:-aml-gitea-act-runner}"
RUNNER_VOLUME="${GITEA_RUNNER_VOLUME:-aml-gitea-act-runner-data}"
RUNNER_IMAGE="${GITEA_ACT_RUNNER_IMAGE:-gitea/act_runner:latest}"
RUNNER_NAME="${GITEA_RUNNER_NAME:-aml-local-runner}"
JOB_IMAGE="${GITEA_RUNNER_JOB_IMAGE:-aml-gitea-job:latest}"
JOB_IMAGE_DOCKERFILE="${GITEA_RUNNER_JOB_IMAGE_DOCKERFILE:-docker/gitea-actions-runner/Dockerfile}"
BUILD_JOB_IMAGE="${GITEA_RUNNER_BUILD_JOB_IMAGE:-true}"
RESET_RUNNER_VOLUME="${GITEA_RUNNER_RESET_VOLUME:-true}"
RUNNER_LABELS="${GITEA_RUNNER_LABELS:-ubuntu-latest:docker://${JOB_IMAGE}}"

token_json="$(api_get "/repos/${OWNER}/${REPO}/actions/runners/registration-token")"
registration_token="$(printf '%s' "$token_json" | jq -r '.token')"

if [ -z "$registration_token" ] || [ "$registration_token" = "null" ]; then
    echo "Failed to get Gitea Actions runner registration token." >&2
    exit 1
fi

echo "=========================================="
echo "  AML Gitea Actions Runner"
echo "=========================================="
echo "  Repository: ${OWNER}/${REPO}"
echo "  Instance:   ${INSTANCE_URL}"
echo "  Container:  ${RUNNER_CONTAINER}"
echo "  Labels:     ${RUNNER_LABELS}"
echo "  Reset data: ${RESET_RUNNER_VOLUME}"
echo ""

if [ "$BUILD_JOB_IMAGE" = "true" ] && [ -f "$JOB_IMAGE_DOCKERFILE" ]; then
    echo "Building local job image: ${JOB_IMAGE}"
    docker build -t "$JOB_IMAGE" -f "$JOB_IMAGE_DOCKERFILE" .
fi

docker pull "$RUNNER_IMAGE"

if docker ps -a --format '{{.Names}}' | grep -Fxq "$RUNNER_CONTAINER"; then
    docker rm -f "$RUNNER_CONTAINER" >/dev/null
fi

if [ "$RESET_RUNNER_VOLUME" = "true" ]; then
    docker volume rm "$RUNNER_VOLUME" >/dev/null 2>&1 || true
fi

docker volume create "$RUNNER_VOLUME" >/dev/null

docker run -d \
    --name "$RUNNER_CONTAINER" \
    --restart unless-stopped \
    "${DOCKER_HOST_ARGS[@]}" \
    -e "GITEA_INSTANCE_URL=${INSTANCE_URL}" \
    -e "GITEA_RUNNER_REGISTRATION_TOKEN=${registration_token}" \
    -e "GITEA_RUNNER_NAME=${RUNNER_NAME}" \
    -e "GITEA_RUNNER_LABELS=${RUNNER_LABELS}" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "${RUNNER_VOLUME}:/data" \
    "$RUNNER_IMAGE" >/dev/null

echo "Runner started. Checking registration status..."
sleep 3
bash scripts/gitea-actions-status.sh
