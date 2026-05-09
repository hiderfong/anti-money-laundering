#!/bin/bash
# ============================================================
# AML System - 端到端 API 测试脚本
# 在应用启动后运行，验证核心 API 功能
# ============================================================

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
E2E_IP="${E2E_IP:-127.0.0.101}"
PASS=0
FAIL=0
TOKEN=""

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

check() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if echo "$actual" | grep -q "$expected"; then
        echo -e "  ${GREEN}✓${NC} $name"
        PASS=$((PASS+1))
    else
        echo -e "  ${RED}✗${NC} $name (expected: $expected)"
        echo "    Response: $actual" | head -3
        FAIL=$((FAIL+1))
    fi
}

auth_get() {
    curl -s "$1" -H "Authorization: Bearer $TOKEN"
}

auth_post() {
    curl -s -X POST "$1" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d "$2"
}

echo "=========================================="
echo "  AML System 端到端 API 测试"
echo "=========================================="

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

RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $E2E_IP" \
    -d '{"username":"admin","password":"admin123"}')
check "登录成功" '"code":200' "$RESP"
TOKEN=$(echo "$RESP" | jq -r '.data.accessToken // empty' 2>/dev/null)

if [ -z "$TOKEN" ]; then
    echo "  WARNING: 无法获取Token，后续测试将跳过认证"
else
    echo "  Token: ${TOKEN:0:20}..."
fi

RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $E2E_IP" \
    -d '{"username":"admin","password":"wrong"}')
check "错误密码返回失败" '"code":401' "$RESP"

# ==================== KYC 模块 ====================
echo ""
echo "[3] KYC 客户管理"

RESP=$(auth_post "$BASE_URL/kyc/customers" '{
    "customerType":"INDIVIDUAL",
    "name":"E2E测试客户",
    "nationality":"CN",
    "idType":"ID_CARD",
    "idNumber":"110101199001011234",
    "phone":"13800138000",
    "email":"e2e@test.com"
}')
check "创建客户" '"code":200' "$RESP"
CUSTOMER_ID=$(echo "$RESP" | jq -r '.data.id // empty' 2>/dev/null)

RESP=$(auth_get "$BASE_URL/kyc/customers/page?page=1&size=10")
check "查询客户列表" '"code":200' "$RESP"

if [ -n "$CUSTOMER_ID" ]; then
    RESP=$(auth_get "$BASE_URL/kyc/customers/$CUSTOMER_ID")
    check "查询客户详情" '"code":200' "$RESP"
fi

# ==================== 交易监测模块 ====================
echo ""
echo "[4] 交易监测"

TXN_NO="TXN$(date +%s)"
RESP=$(auth_post "$BASE_URL/monitoring/transactions/ingest" "{
    \"transactionNo\":\"$TXN_NO\",
    \"customerId\":${CUSTOMER_ID:-1},
    \"transactionType\":\"PREMIUM\",
    \"amount\":60000,
    \"currency\":\"CNY\",
    \"paymentMethod\":\"CASH\",
    \"transactionTime\":\"2026-05-01 10:00:00\"
}")
check "录入交易" '"code":200' "$RESP"

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

RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/doc.html")
check "Swagger文档可访问" "200" "$RESP"

# ==================== 结果汇总 ====================
echo ""
echo "=========================================="
echo -e "  测试结果: ${GREEN}${PASS} 通过${NC} / ${RED}${FAIL} 失败${NC}"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
