#!/bin/bash
# ============================================================================
# AML System - CI npm wrapper
# Retries npm commands that can fail on transient registry/network resets in
# fresh Gitea act_runner containers.
# ============================================================================

set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <npm-command-or-args...>" >&2
    exit 2
fi

attempts="${CI_NPM_ATTEMPTS:-3}"
base_delay="${CI_NPM_RETRY_DELAY_SECONDS:-10}"
attempt_timeout="${CI_NPM_ATTEMPT_TIMEOUT_SECONDS:-300}"

export NPM_CONFIG_FETCH_RETRIES="${NPM_CONFIG_FETCH_RETRIES:-5}"
export NPM_CONFIG_FETCH_RETRY_FACTOR="${NPM_CONFIG_FETCH_RETRY_FACTOR:-2}"
export NPM_CONFIG_FETCH_RETRY_MINTIMEOUT="${NPM_CONFIG_FETCH_RETRY_MINTIMEOUT:-10000}"
export NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT="${NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT:-120000}"
export NPM_CONFIG_AUDIT="${NPM_CONFIG_AUDIT:-false}"
export NPM_CONFIG_FUND="${NPM_CONFIG_FUND:-false}"

run_npm() {
    if command -v timeout >/dev/null 2>&1; then
        timeout_args=()
        if timeout --help 2>/dev/null | grep -q -- "--foreground"; then
            timeout_args=(--foreground)
        fi
        timeout "${timeout_args[@]}" "$attempt_timeout" npm "$@"
    else
        npm "$@"
    fi
}

for attempt in $(seq 1 "$attempts"); do
    echo "npm attempt ${attempt}/${attempts} (${attempt_timeout}s timeout): npm $*"
    set +e
    run_npm "$@"
    status=$?
    set -e

    if [ "$status" -eq 0 ]; then
        exit 0
    fi

    if [ "$attempt" -ge "$attempts" ]; then
        exit "$status"
    fi

    echo "npm attempt ${attempt} failed with exit code ${status}; verifying cache before retry."
    npm cache verify || true
    sleep $((base_delay * attempt))
done
