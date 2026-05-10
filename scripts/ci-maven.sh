#!/bin/bash
# ============================================================================
# AML System - CI Maven wrapper
# Retries Maven commands that can fail on transient dependency transfer issues
# in fresh Gitea act_runner containers.
# ============================================================================

set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <maven-goal-or-args...>" >&2
    exit 2
fi

attempts="${CI_MAVEN_ATTEMPTS:-3}"
retry_count="${MAVEN_HTTP_RETRY_COUNT:-5}"
base_delay="${CI_MAVEN_RETRY_DELAY_SECONDS:-10}"

for attempt in $(seq 1 "$attempts"); do
    echo "Maven attempt ${attempt}/${attempts}: mvn -B -ntp -Dmaven.wagon.http.retryHandler.count=${retry_count} $*"
    if mvn -B -ntp -Dmaven.wagon.http.retryHandler.count="$retry_count" "$@"; then
        exit 0
    fi

    status=$?
    if [ "$attempt" -ge "$attempts" ]; then
        exit "$status"
    fi

    echo "Maven attempt ${attempt} failed with exit code ${status}; cleaning partial transfer markers before retry."
    find "${HOME}/.m2/repository" -name "*.lastUpdated" -delete 2>/dev/null || true
    sleep $((base_delay * attempt))
done
