#!/bin/bash
# ============================================================================
# AML System - Local Gitea checkout helper
# Avoids downloading actions/checkout from GitHub in local act_runner jobs.
# ============================================================================

set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$PWD}"
repository="${GITHUB_REPOSITORY:-}"
server_url="${GITHUB_SERVER_URL:-}"
sha="${GITHUB_SHA:-}"
ref="${GITHUB_REF:-}"
ref_name="${GITHUB_REF_NAME:-ci}"

if [ -z "$repository" ] || [ -z "$server_url" ]; then
    echo "GITHUB_REPOSITORY and GITHUB_SERVER_URL are required for local checkout." >&2
    exit 2
fi

remote_url="${server_url%/}/${repository}"

if [ -f /.dockerenv ]; then
    remote_url="${remote_url/localhost:/host.docker.internal:}"
    remote_url="${remote_url/127.0.0.1:/host.docker.internal:}"
fi

mkdir -p "$workspace"
cd "$workspace"

if [ ! -d .git ]; then
    git init
fi

if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$remote_url"
else
    git remote add origin "$remote_url"
fi

git config --local gc.auto 0
git config --local --add safe.directory "$workspace" || true

fetch_args=(--no-tags --prune --depth=1 origin)
if [ -n "$sha" ]; then
    fetch_args+=("$sha")
elif [ -n "$ref" ]; then
    fetch_args+=("+${ref}:refs/remotes/origin/${ref_name}")
else
    echo "GITHUB_SHA or GITHUB_REF is required for local checkout." >&2
    exit 2
fi

token="${GITEA_CHECKOUT_TOKEN:-${GITHUB_TOKEN:-}}"
headers=()
if [ -n "$token" ]; then
    headers+=("bearer ${token}")
    headers+=("token ${token}")
    basic_token="$(printf 'x-access-token:%s' "$token" | base64 | tr -d '\n')"
    headers+=("basic ${basic_token}")
fi

fetch_status=1
if [ "${#headers[@]}" -eq 0 ]; then
    git fetch "${fetch_args[@]}"
    fetch_status=0
else
    for header in "${headers[@]}"; do
        if git -c "http.extraheader=AUTHORIZATION: ${header}" fetch "${fetch_args[@]}"; then
            fetch_status=0
            break
        fi
    done
fi

if [ "$fetch_status" -ne 0 ]; then
    echo "Failed to fetch ${repository} from local Gitea." >&2
    exit "$fetch_status"
fi

if [ -n "$sha" ]; then
    git checkout --force "$sha"
else
    git checkout --force "refs/remotes/origin/${ref_name}"
fi

git clean -ffdx
git log -1 --format='Checked out %H %s'
