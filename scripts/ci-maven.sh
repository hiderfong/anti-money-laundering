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
attempt_timeout="${CI_MAVEN_ATTEMPT_TIMEOUT_SECONDS:-900}"
connect_timeout="${MAVEN_HTTP_CONNECT_TIMEOUT_MS:-30000}"
read_timeout="${MAVEN_HTTP_READ_TIMEOUT_MS:-60000}"
connection_ttl="${MAVEN_HTTP_CONNECTION_TTL_SECONDS:-120}"
settings_file="${CI_MAVEN_SETTINGS_FILE:-}"
default_mirror_enabled="${CI_MAVEN_USE_DEFAULT_MIRROR:-true}"
default_mirror_url="${CI_MAVEN_MIRROR_URL:-https://maven.aliyun.com/repository/public}"

maven_args=(
    -B
    -ntp
    "-Dmaven.wagon.http.retryHandler.count=${retry_count}"
    "-Dmaven.wagon.rto=${read_timeout}"
    "-Daether.connector.connectTimeout=${connect_timeout}"
    "-Daether.connector.requestTimeout=${read_timeout}"
    "-Dmaven.wagon.httpconnectionManager.ttlSeconds=${connection_ttl}"
)

if [ -n "$settings_file" ]; then
    maven_args+=(-s "$settings_file")
elif [ "$default_mirror_enabled" = "true" ]; then
    generated_settings="$(mktemp)"
    cat > "$generated_settings" <<EOF
<settings>
  <mirrors>
    <mirror>
      <id>aml-ci-central-mirror</id>
      <name>AML CI Maven Central Mirror</name>
      <url>${default_mirror_url}</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
    maven_args+=(-s "$generated_settings")
    trap 'rm -f "$generated_settings"' EXIT
fi

run_maven() {
    if command -v timeout >/dev/null 2>&1; then
        timeout_args=()
        if timeout --help 2>/dev/null | grep -q -- "--foreground"; then
            timeout_args=(--foreground)
        fi
        timeout "${timeout_args[@]}" "$attempt_timeout" mvn "${maven_args[@]}" "$@"
    else
        mvn "${maven_args[@]}" "$@"
    fi
}

for attempt in $(seq 1 "$attempts"); do
    echo "Maven attempt ${attempt}/${attempts} (${attempt_timeout}s timeout): mvn ${maven_args[*]} $*"
    set +e
    run_maven "$@"
    status=$?
    set -e

    if [ "$status" -eq 0 ]; then
        exit 0
    fi

    if [ "$attempt" -ge "$attempts" ]; then
        exit "$status"
    fi

    echo "Maven attempt ${attempt} failed with exit code ${status}; cleaning partial transfer markers before retry."
    find "${HOME}/.m2/repository" -name "*.lastUpdated" -delete 2>/dev/null || true
    sleep $((base_delay * attempt))
done
