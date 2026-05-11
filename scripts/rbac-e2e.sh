#!/bin/bash
# ============================================================================
# AML System - RBAC E2E 验收脚本
# 前提：后端 :8080 已启动；四个 e2e_* 账号已通过 prepare-rbac-e2e-users.sh 写入数据库
# ============================================================================

set -e

API_URL="${API_URL:-http://localhost:8080/api}"
PASSWORD="${PASSWORD:-admin123}"
E2E_RUN_ID="${E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
E2E_PREFIX="${E2E_PREFIX:-E2E}"
E2E_IP="${RBAC_E2E_IP:-${E2E_IP:-127.0.0.104}}"
E2E_CODE_ID="$(printf "%s" "$E2E_RUN_ID" | tr -cd '[:alnum:]' | cut -c1-14)"
if [ -z "$E2E_CODE_ID" ]; then
    E2E_CODE_ID="$(date +%m%d%H%M%S)"
fi

PASS=0
FAIL=0
TOTAL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

compact_response() {
    local body="$1"
    if command -v jq >/dev/null 2>&1 && echo "$body" | jq -e . >/dev/null 2>&1; then
        echo "$body" | jq -c .
    else
        printf "%s" "$body"
    fi
}

json_field() {
    local body="$1"
    local field="$2"
    if command -v jq >/dev/null 2>&1 && echo "$body" | jq -e . >/dev/null 2>&1; then
        echo "$body" | jq -r "$field // empty"
    else
        echo "$body" | grep -o "\"${field##*.}\":\"[^\"]*\"" | head -n 1 | cut -d'"' -f4
    fi
}

pass() { ((PASS+=1)); ((TOTAL+=1)); echo -e "  ${GREEN}✓ $1${NC}"; }
fail() {
    ((FAIL+=1))
    ((TOTAL+=1))
    echo -e "  ${RED}✗ $1${NC}"
    if [ -n "${2:-}" ]; then
        printf "    Detail: %.1200s\n" "$(compact_response "$2")"
    fi
}
info() { echo -e "${YELLOW}▶ $1${NC}"; }

login() {
    local username="$1"
    local response
    response=$(curl -s -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: $E2E_IP" \
        -H "X-E2E-Run-Id: $E2E_RUN_ID" \
        -d "{\"username\":\"$username\",\"password\":\"$PASSWORD\"}" 2>/dev/null || true)

    local token
    token=$(json_field "$response" ".data.accessToken")
    if [ -n "$token" ] && [ "$token" != "null" ]; then
        printf "%s" "$token"
        return 0
    fi

    printf "%s" "$response" >&2
    return 1
}

request() {
    local method="$1"
    local path="$2"
    local token="$3"
    local body="${4:-}"

    if [ -n "$body" ]; then
        curl -s -w "\n%{http_code}" -X "$method" "$API_URL$path" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" \
            -H "X-E2E-Run-Id: $E2E_RUN_ID" \
            -d "$body" 2>/dev/null || true
    else
        curl -s -w "\n%{http_code}" -X "$method" "$API_URL$path" \
            -H "Authorization: Bearer $token" \
            -H "X-E2E-Run-Id: $E2E_RUN_ID" 2>/dev/null || true
    fi
}

http_code_of() {
    local response="$1"
    printf "%s" "$response" | tail -n 1
}

body_of() {
    local response="$1"
    printf "%s" "$response" | sed '$d'
}

expect_status() {
    local label="$1"
    local expected="$2"
    local response="$3"
    local http
    local body
    http="$(http_code_of "$response")"
    body="$(body_of "$response")"
    if [ "$http" = "$expected" ]; then
        pass "$label (HTTP $http)"
    else
        fail "$label，期望 HTTP $expected，实际 HTTP $http" "$body"
    fi
}

assert_body_contains() {
    local label="$1"
    local body="$2"
    local expected="$3"
    if echo "$body" | grep -q "$expected"; then
        pass "$label 包含 $expected"
    else
        fail "$label 缺少 $expected" "$body"
    fi
}

echo ""
echo "=========================================="
echo "  AML RBAC E2E 验收"
echo "=========================================="
echo "  API_URL: $API_URL"
echo "  E2E_RUN_ID: $E2E_RUN_ID"
echo "  E2E_IP: $E2E_IP"
echo ""

info "1. 登录四类角色账号"
ADMIN_TOKEN=$(login "admin") && pass "admin 登录成功" || { fail "admin 登录失败" "$ADMIN_TOKEN"; ADMIN_TOKEN=""; }
COMPLIANCE_TOKEN=$(login "e2e_compliance") && pass "e2e_compliance 登录成功" || { fail "e2e_compliance 登录失败，请先执行 scripts/prepare-rbac-e2e-users.sh --execute" "$COMPLIANCE_TOKEN"; COMPLIANCE_TOKEN=""; }
INVESTIGATOR_TOKEN=$(login "e2e_investigator") && pass "e2e_investigator 登录成功" || { fail "e2e_investigator 登录失败，请先执行 scripts/prepare-rbac-e2e-users.sh --execute" "$INVESTIGATOR_TOKEN"; INVESTIGATOR_TOKEN=""; }
VIEWER_TOKEN=$(login "e2e_viewer") && pass "e2e_viewer 登录成功" || { fail "e2e_viewer 登录失败，请先执行 scripts/prepare-rbac-e2e-users.sh --execute" "$VIEWER_TOKEN"; VIEWER_TOKEN=""; }

info "2. /auth/me 前端权限契约"
if [ -n "$ADMIN_TOKEN" ]; then
    RESP=$(request GET "/auth/me" "$ADMIN_TOKEN")
    BODY=$(body_of "$RESP")
    expect_status "admin /auth/me" "200" "$RESP"
    assert_body_contains "admin roles" "$BODY" "ROLE_ADMIN"
    assert_body_contains "admin permissions" "$BODY" "system:user"
fi
if [ -n "$COMPLIANCE_TOKEN" ]; then
    RESP=$(request GET "/auth/me" "$COMPLIANCE_TOKEN")
    BODY=$(body_of "$RESP")
    expect_status "e2e_compliance /auth/me" "200" "$RESP"
    assert_body_contains "e2e_compliance roles" "$BODY" "ROLE_COMPLIANCE"
    assert_body_contains "e2e_compliance permissions" "$BODY" "product:manage"
fi
if [ -n "$INVESTIGATOR_TOKEN" ]; then
    RESP=$(request GET "/auth/me" "$INVESTIGATOR_TOKEN")
    BODY=$(body_of "$RESP")
    expect_status "e2e_investigator /auth/me" "200" "$RESP"
    assert_body_contains "e2e_investigator roles" "$BODY" "ROLE_INVESTIGATOR"
    assert_body_contains "e2e_investigator permissions" "$BODY" "customer:create"
fi
if [ -n "$VIEWER_TOKEN" ]; then
    RESP=$(request GET "/auth/me" "$VIEWER_TOKEN")
    BODY=$(body_of "$RESP")
    expect_status "e2e_viewer /auth/me" "200" "$RESP"
    assert_body_contains "e2e_viewer roles" "$BODY" "ROLE_VIEWER"
    assert_body_contains "e2e_viewer permissions" "$BODY" "customer:view"
fi

info "3. 后端写接口 403 权限校验"
VIEWER_CUSTOMER_BODY=$(cat <<JSON
{
  "customerType": "INDIVIDUAL",
  "name": "${E2E_PREFIX}RBAC拒绝客户_${E2E_RUN_ID}",
  "gender": "MALE",
  "nationality": "CN",
  "idType": "IDCARD",
  "idNumber": "9${E2E_RUN_ID}001",
  "phone": "13800138000",
  "email": "rbac-viewer-${E2E_RUN_ID}@test.local"
}
JSON
)

PRODUCT_BODY=$(cat <<JSON
{
  "productCode": "${E2E_PREFIX}_RP_${E2E_CODE_ID}",
  "productName": "${E2E_PREFIX}RBAC产品_${E2E_RUN_ID}",
  "productType": "LIFE"
}
JSON
)

USER_BODY=$(cat <<JSON
{
  "username": "e2e_rbac_temp_${E2E_RUN_ID}",
  "password": "admin123",
  "realName": "E2E RBAC 临时用户 ${E2E_RUN_ID}",
  "email": "e2e_rbac_temp_${E2E_RUN_ID}@test.local"
}
JSON
)

if [ -n "$VIEWER_TOKEN" ]; then
    RESP=$(request POST "/kyc/customers" "$VIEWER_TOKEN" "$VIEWER_CUSTOMER_BODY")
    expect_status "只读用户不能创建客户" "403" "$RESP"

    TXN_BODY="{\"transactionNo\":\"${E2E_PREFIX}_RBAC_TXN_${E2E_RUN_ID}\",\"customerId\":1,\"transactionType\":\"PREMIUM\",\"amount\":1000,\"currency\":\"CNY\",\"paymentMethod\":\"TRANSFER\",\"transactionTime\":\"$(date +%Y-%m-%dT%H:%M:%S)\"}"
    RESP=$(request POST "/monitoring/transactions/ingest" "$VIEWER_TOKEN" "$TXN_BODY")
    expect_status "只读用户不能录入交易" "403" "$RESP"
fi

if [ -n "$INVESTIGATOR_TOKEN" ]; then
    RESP=$(request POST "/products" "$INVESTIGATOR_TOKEN" "$PRODUCT_BODY")
    expect_status "调查员不能创建产品" "403" "$RESP"
fi

if [ -n "$COMPLIANCE_TOKEN" ]; then
    RESP=$(request POST "/system/users" "$COMPLIANCE_TOKEN" "$USER_BODY")
    expect_status "合规专员不能创建系统用户" "403" "$RESP"
fi

info "4. 允许路径与新建用户状态"
if [ -n "$INVESTIGATOR_TOKEN" ]; then
    INVESTIGATOR_CUSTOMER_BODY=$(echo "$VIEWER_CUSTOMER_BODY" | sed "s/RBAC拒绝客户/RBAC允许客户/g; s/001/002/g; s/rbac-viewer/rbac-investigator/g")
    RESP=$(request POST "/kyc/customers" "$INVESTIGATOR_TOKEN" "$INVESTIGATOR_CUSTOMER_BODY")
    expect_status "调查员可创建客户" "200" "$RESP"
fi

if [ -n "$COMPLIANCE_TOKEN" ]; then
    RESP=$(request POST "/products" "$COMPLIANCE_TOKEN" "$PRODUCT_BODY")
    expect_status "合规专员可创建产品" "200" "$RESP"
fi

if [ -n "$ADMIN_TOKEN" ]; then
    RESP=$(request POST "/system/users" "$ADMIN_TOKEN" "$USER_BODY")
    expect_status "管理员可创建系统用户" "200" "$RESP"

    NEW_USER_TOKEN=$(login "e2e_rbac_temp_${E2E_RUN_ID}") && pass "API 新建用户可直接登录" || { fail "API 新建用户无法登录，需检查默认状态是否为 ENABLED" "$NEW_USER_TOKEN"; NEW_USER_TOKEN=""; }
fi

echo ""
echo "=========================================="
echo "  RBAC E2E 测试完成"
echo "=========================================="
echo -e "  通过: ${GREEN}$PASS${NC}"
echo -e "  失败: ${RED}$FAIL${NC}"
echo "  总计: $TOTAL"
echo ""

if [ $FAIL -gt 0 ]; then
    exit 1
fi
