#!/bin/bash
# ============================================================================
# AML System - 生产发布前配置检查
# 用法: bash scripts/prod-readiness-check.sh [.env]
# ============================================================================

set -euo pipefail

ENV_FILE="${1:-.env}"
PASS=0
WARN=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { PASS=$((PASS + 1)); echo -e "  ${GREEN}✓${NC} $1"; }
warn_check() { WARN=$((WARN + 1)); echo -e "  ${YELLOW}!${NC} $1"; }
fail() { FAIL=$((FAIL + 1)); echo -e "  ${RED}✗${NC} $1"; }
info() { echo -e "${YELLOW}▶ $1${NC}"; }

is_blank() {
    local value="${1:-}"
    [ -z "$value" ]
}

is_placeholder() {
    local value="${1:-}"
    [[ "$value" == *CHANGE_ME* || "$value" == *TODO* || "$value" == *example.com* ]]
}

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf "%s" "$value"
}

profile_contains() {
    local profile="$1"
    local profiles=",${SPRING_PROFILES_ACTIVE:-},"
    [[ "$profiles" == *",$profile,"* ]]
}

require_var() {
    local name="$1"
    local value="${!name:-}"
    if is_blank "$value"; then
        fail "$name 未设置"
    elif is_placeholder "$value"; then
        fail "$name 仍是占位值"
    else
        pass "$name 已设置"
    fi
}

reject_placeholder_if_set() {
    local name="$1"
    local value="${!name:-}"
    if is_blank "$value"; then
        pass "$name 未设置（可选）"
    elif is_placeholder "$value"; then
        fail "$name 仍是占位值"
    else
        pass "$name 已设置"
    fi
}

require_when_enabled() {
    local flag_name="$1"
    local name="$2"
    local enabled="${!flag_name:-true}"
    if [ "$enabled" = "false" ]; then
        local value="${!name:-}"
        if is_placeholder "$value"; then
            fail "$name 已禁用但仍是占位值"
        else
            pass "$name 跳过（$flag_name=false）"
        fi
    else
        require_var "$name"
    fi
}

require_positive_int_if_set() {
    local name="$1"
    local value="${!name:-}"
    if is_blank "$value"; then
        pass "$name 未设置（使用默认值）"
    elif [[ "$value" =~ ^[0-9]+$ ]] && [ "$value" -gt 0 ]; then
        pass "$name 是正整数"
    else
        fail "$name 必须是正整数"
    fi
}

validate_env_file_permissions() {
    local mode
    mode="$(stat -f "%Lp" "$ENV_FILE" 2>/dev/null || stat -c "%a" "$ENV_FILE" 2>/dev/null || true)"
    if [ -z "$mode" ]; then
        warn_check "无法读取环境文件权限，请人工确认 .env 不对无关用户开放"
        return
    fi

    if [ "${mode: -1}" != "0" ]; then
        warn_check "${ENV_FILE} 权限为 ${mode}，建议生产环境执行 chmod 600 ${ENV_FILE} 或 chmod 640 ${ENV_FILE}"
    else
        pass "$ENV_FILE 未向其他用户开放读取权限"
    fi
}

validate_cors_origins() {
    if is_blank "${AML_CORS_ORIGINS:-}" || is_placeholder "${AML_CORS_ORIGINS:-}"; then
        return
    fi

    local has_invalid=false
    local origin
    IFS=',' read -r -a origins <<< "$AML_CORS_ORIGINS"
    for origin in "${origins[@]}"; do
        origin="$(trim "$origin")"
        if [ -z "$origin" ]; then
            fail "AML_CORS_ORIGINS 包含空白域名"
            has_invalid=true
        elif [[ "$origin" == *"*"* ]]; then
            fail "AML_CORS_ORIGINS 不能包含通配符: $origin"
            has_invalid=true
        elif [[ "$origin" == *"localhost"* || "$origin" == *"127.0.0.1"* ]]; then
            fail "AML_CORS_ORIGINS 生产环境不能包含本机地址: $origin"
            has_invalid=true
        elif [[ "$origin" != https://* ]]; then
            fail "AML_CORS_ORIGINS 生产环境必须使用 HTTPS: $origin"
            has_invalid=true
        fi
    done

    if [ "$has_invalid" = "false" ]; then
        pass "AML_CORS_ORIGINS 看起来适合生产"
    fi
}

echo ""
echo "=========================================="
echo "  AML 生产发布前配置检查"
echo "=========================================="
echo "  ENV_FILE: $ENV_FILE"
echo ""

if [ ! -f "$ENV_FILE" ]; then
    fail "环境文件不存在: $ENV_FILE"
    exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

info "1. 基础生产配置"
validate_env_file_permissions

if profile_contains "prod"; then
    pass "SPRING_PROFILES_ACTIVE 包含 prod"
else
    fail "SPRING_PROFILES_ACTIVE 必须包含 prod"
fi
for forbidden_profile in dev test local; do
    if profile_contains "$forbidden_profile"; then
        fail "SPRING_PROFILES_ACTIVE 生产发布不能包含 $forbidden_profile"
    fi
done

require_var AML_DB_HOST
require_positive_int_if_set AML_DB_PORT
require_var AML_DB_NAME
require_var AML_DB_USER
require_var AML_DB_PASSWORD

info "2. 外部依赖配置"
require_when_enabled AML_REDIS_ENABLED AML_REDIS_HOST
require_when_enabled AML_REDIS_ENABLED AML_REDIS_PASSWORD
require_when_enabled AML_KAFKA_ENABLED AML_KAFKA_BROKERS
require_when_enabled AML_NEO4J_ENABLED AML_NEO4J_URI
require_when_enabled AML_NEO4J_ENABLED AML_NEO4J_USERNAME
require_when_enabled AML_NEO4J_ENABLED AML_NEO4J_PASSWORD
require_var AML_ES_URIS
require_var AML_MINIO_ENDPOINT
require_var AML_MINIO_ACCESS_KEY
require_var AML_MINIO_SECRET_KEY
require_var AML_MINIO_BUCKET

info "3. 安全密钥"
require_var AML_JWT_SECRET
if [ -n "${AML_JWT_SECRET:-}" ] && ! is_placeholder "$AML_JWT_SECRET"; then
    if ! command -v openssl >/dev/null 2>&1; then
        fail "缺少 openssl，无法校验 AML_JWT_SECRET"
    elif printf "%s" "$AML_JWT_SECRET" | openssl base64 -d -A >/dev/null 2>&1; then
        pass "AML_JWT_SECRET 是可解码 Base64"
    else
        fail "AML_JWT_SECRET 不是有效 Base64"
    fi
fi

require_var AML_ENCRYPTION_KEY
if [ -n "${AML_ENCRYPTION_KEY:-}" ] && ! is_placeholder "$AML_ENCRYPTION_KEY"; then
    if [ "${#AML_ENCRYPTION_KEY}" -eq 32 ]; then
        pass "AML_ENCRYPTION_KEY 长度为 32"
    else
        fail "AML_ENCRYPTION_KEY 必须为 32 个字符"
    fi
fi

info "4. CORS 与暴露面"
require_var AML_CORS_ORIGINS
validate_cors_origins

if [ "${AML_API_DOCS_ENABLED:-false}" = "true" ]; then
    fail "AML_API_DOCS_ENABLED 生产环境建议为 false"
else
    pass "AML_API_DOCS_ENABLED 未开启"
fi

info "5. 可选配置占位符"
reject_placeholder_if_set AML_DB_URL
reject_placeholder_if_set AML_ES_USERNAME
reject_placeholder_if_set AML_ES_PASSWORD
reject_placeholder_if_set AML_KAFKA_TOPIC
reject_placeholder_if_set AML_KAFKA_CONSUMER_GROUP

echo ""
echo "=========================================="
echo -e "  检查结果: ${GREEN}$PASS 通过${NC} / ${YELLOW}$WARN 警告${NC} / ${RED}$FAIL 失败${NC}"
echo "=========================================="
echo ""

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
