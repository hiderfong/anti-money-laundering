#!/bin/bash
# ============================================================
# AML System - 端到端 API 测试脚本
# 在应用启动后运行，验证核心 API 功能
# ============================================================

set -o pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
E2E_IP="${E2E_IP:-127.0.0.101}"
E2E_RUN_ID="${E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
E2E_PREFIX="${E2E_PREFIX:-E2E}"
PASS=0
FAIL=0
TOKEN=""

RUN_NUM="$(printf '%s' "$E2E_RUN_ID" | tr -cd '0-9')"
if [ -z "$RUN_NUM" ]; then
    RUN_NUM="$(date +%H%M%S)"
fi
if [ ${#RUN_NUM} -gt 8 ]; then
    RUN_TAIL="${RUN_NUM:$((${#RUN_NUM}-8))}"
else
    RUN_TAIL="$RUN_NUM"
fi
RUN_TAIL=$(printf "%08d" "$((10#$RUN_TAIL % 100000000))")
ID_TAIL=$(printf "%04d" "$((10#$RUN_TAIL % 10000))")

E2E_CUSTOMER_NAME="${E2E_CUSTOMER_NAME:-${E2E_PREFIX}客户_${E2E_RUN_ID}}"
E2E_ID_NUMBER="${E2E_ID_NUMBER:-11010119900101${ID_TAIL}}"
E2E_PHONE="${E2E_PHONE:-139${RUN_TAIL}}"
E2E_EMAIL="${E2E_EMAIL:-e2e_${E2E_RUN_ID}@test.local}"
E2E_TXN_NO="${E2E_TXN_NO:-${E2E_PREFIX}_TXN_${E2E_RUN_ID}_001}"

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

compact_response() {
    local body="$1"
    if command -v jq >/dev/null 2>&1 && echo "$body" | jq -e . >/dev/null 2>&1; then
        echo "$body" | jq -c .
    else
        printf "%s" "$body"
    fi
}

check() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo -e "  ${GREEN}✓${NC} $name"
        PASS=$((PASS+1))
    else
        echo -e "  ${RED}✗${NC} $name (expected: $expected)"
        printf "    Response: %.1000s\n" "$(compact_response "$actual")"
        FAIL=$((FAIL+1))
    fi
}

auth_get() {
    curl -sS "$1" -H "Authorization: Bearer $TOKEN" -H "X-E2E-Run-Id: $E2E_RUN_ID"
}

auth_post() {
    curl -sS -X POST "$1" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-E2E-Run-Id: $E2E_RUN_ID" \
        -d "$2"
}

echo "=========================================="
echo "  AML System 端到端 API 测试"
echo "=========================================="
echo "  BASE_URL: $BASE_URL"
echo "  E2E_RUN_ID: $E2E_RUN_ID"
echo "  E2E_PREFIX: $E2E_PREFIX"

# ==================== 系统模块 ====================
echo ""
echo "[1] 系统健康检查"

RESP=$(auth_get "$BASE_URL/system/health")
check "健康检查" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/system/info")
check "系统信息" '"code":200' "$RESP"

# ==================== 认证模块 ====================
echo ""
echo "[2] 认证模块"

RESP=$(curl -sS -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $E2E_IP" \
    -H "X-E2E-Run-Id: $E2E_RUN_ID" \
    -d '{"username":"admin","password":"Aml@Admin#2026!"}')
check "登录成功" '"code":200' "$RESP"
TOKEN=$(echo "$RESP" | jq -r '.data.accessToken // empty' 2>/dev/null)

if [ -z "$TOKEN" ]; then
    echo "  WARNING: 无法获取Token，后续测试将跳过认证"
else
    echo "  Token acquired"
fi

RESP=$(curl -sS -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $E2E_IP" \
    -H "X-E2E-Run-Id: $E2E_RUN_ID" \
    -d '{"username":"admin","password":"wrong"}')
check "错误密码返回失败" '"code":401' "$RESP"

# ==================== KYC 模块 ====================
echo ""
echo "[3] KYC 客户管理"

RESP=$(auth_post "$BASE_URL/kyc/customers" '{
    "customerType":"INDIVIDUAL",
    "name":"'"$E2E_CUSTOMER_NAME"'",
    "nationality":"CN",
    "idType":"IDCARD",
    "idNumber":"'"$E2E_ID_NUMBER"'",
    "phone":"'"$E2E_PHONE"'",
    "email":"'"$E2E_EMAIL"'"
}')
check "创建客户" '"code":200' "$RESP"
CUSTOMER_ID=$(echo "$RESP" | jq -r '.data.id // empty' 2>/dev/null)
echo "  E2E客户: ${E2E_CUSTOMER_NAME} (id=${CUSTOMER_ID:-N/A})"

RESP=$(auth_get "$BASE_URL/kyc/customers/page?page=1&size=10")
check "查询客户列表" '"code":200' "$RESP"

if [ -n "$CUSTOMER_ID" ]; then
    RESP=$(auth_get "$BASE_URL/kyc/customers/$CUSTOMER_ID")
    check "查询客户详情" '"code":200' "$RESP"
fi

# ==================== 交易监测模块 ====================
echo ""
echo "[4] 交易监测"

RESP=$(auth_post "$BASE_URL/monitoring/transactions/ingest" "{
    \"transactionNo\":\"$E2E_TXN_NO\",
    \"customerId\":${CUSTOMER_ID:-1},
    \"transactionType\":\"PREMIUM\",
    \"amount\":60000,
    \"currency\":\"CNY\",
    \"paymentMethod\":\"CASH\",
    \"transactionTime\":\"2026-05-01 10:00:00\"
}")
check "录入交易" '"code":200' "$RESP"
echo "  E2E交易流水: $E2E_TXN_NO"

RESP=$(auth_get "$BASE_URL/monitoring/transactions/page?page=1&size=10")
check "查询交易列表" '"code":200' "$RESP"

# ==================== 名单筛查模块 ====================
echo ""
echo "[5] 名单筛查"

RESP=$(auth_post "$BASE_URL/screening/screen" \
    "{\"customerId\":${CUSTOMER_ID:-1},\"screeningType\":\"SANCTIONS\"}")
check "触发筛查" '"code":200' "$RESP"

# ==================== 预警模块 ====================
echo ""
echo "[6] 预警管理"

RESP=$(auth_get "$BASE_URL/alerts/page?page=1&size=10")
check "查询预警列表" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/alerts/statistics")
check "预警统计" '"code":200' "$RESP"

# ==================== 案件模块 ====================
echo ""
echo "[7] 案件管理"

RESP=$(auth_get "$BASE_URL/cases/page?page=1&size=10")
check "查询案件列表" '"code":200' "$RESP"

# ==================== 报送模块 ====================
echo ""
echo "[8] 监管报送"

RESP=$(auth_get "$BASE_URL/reporting/large-txn/page?page=1&size=10")
check "查询大额交易报告" '"code":200' "$RESP"

# ==================== 产品模块 ====================
echo ""
echo "[9] 产品管理"

RESP=$(auth_get "$BASE_URL/products/page?page=1&size=10")
check "查询产品列表" '"code":200' "$RESP"

# ==================== 系统管理 ====================
echo ""
echo "[10] 系统管理"

RESP=$(auth_get "$BASE_URL/system/dicts")
check "查询数据字典" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/system/notifications/unread-count")
check "未读通知数" '"code":200' "$RESP"

# ==================== Swagger ====================
echo ""
echo "[11] API 文档"

RESP=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/doc.html")
check "Swagger文档可访问" "200" "$RESP"

# ==================== 结果汇总 ====================
echo ""
echo "=========================================="
echo -e "  测试结果: ${GREEN}${PASS} 通过${NC} / ${RED}${FAIL} 失败${NC}"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
