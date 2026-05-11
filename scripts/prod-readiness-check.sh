#!/bin/bash
# ============================================================================
# AML System - 生产发布前配置检查
# 用法: bash scripts/prod-readiness-check.sh [.env]
# ============================================================================

set -euo pipefail

ENV_FILE="${1:-.env}"
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { PASS=$((PASS + 1)); echo -e "  ${GREEN}✓${NC} $1"; }
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

require_when_enabled() {
    local flag_name="$1"
    local name="$2"
    local enabled="${!flag_name:-true}"
    if [ "$enabled" = "false" ]; then
        pass "$name 跳过（$flag_name=false）"
    else
        require_var "$name"
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
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *",prod,"* || "${SPRING_PROFILES_ACTIVE:-}" == "prod" || "${SPRING_PROFILES_ACTIVE:-}" == prod,* || "${SPRING_PROFILES_ACTIVE:-}" == *,prod ]]; then
    pass "SPRING_PROFILES_ACTIVE 包含 prod"
else
    fail "SPRING_PROFILES_ACTIVE 必须包含 prod"
fi

require_var AML_DB_HOST
require_var AML_DB_NAME
require_var AML_DB_USER
require_var AML_DB_PASSWORD

info "2. 外部依赖配置"
require_when_enabled AML_REDIS_ENABLED AML_REDIS_HOST
require_when_enabled AML_REDIS_ENABLED AML_REDIS_PASSWORD
require_when_enabled AML_KAFKA_ENABLED AML_KAFKA_BROKERS
require_when_enabled AML_NEO4J_ENABLED AML_NEO4J_URI
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
if [ -n "${AML_CORS_ORIGINS:-}" ] && ! is_placeholder "$AML_CORS_ORIGINS"; then
    if [[ "$AML_CORS_ORIGINS" == *"*"* || "$AML_CORS_ORIGINS" == *"localhost"* || "$AML_CORS_ORIGINS" == *"127.0.0.1"* ]]; then
        fail "AML_CORS_ORIGINS 生产环境不能包含通配符或本机地址"
    elif [[ "$AML_CORS_ORIGINS" == http://* || "$AML_CORS_ORIGINS" == *,http://* ]]; then
        fail "AML_CORS_ORIGINS 生产环境应使用 HTTPS"
    else
        pass "AML_CORS_ORIGINS 看起来适合生产"
    fi
fi

if [ "${AML_API_DOCS_ENABLED:-false}" = "true" ]; then
    fail "AML_API_DOCS_ENABLED 生产环境建议为 false"
else
    pass "AML_API_DOCS_ENABLED 未开启"
fi

echo ""
echo "=========================================="
echo -e "  检查结果: ${GREEN}$PASS 通过${NC} / ${RED}$FAIL 失败${NC}"
echo "=========================================="
echo ""

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
