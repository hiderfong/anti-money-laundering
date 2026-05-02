#!/bin/bash
# ============================================================
# AML System - 端到端 API 测试脚本
# 在应用启动后运行，验证核心 API 功能
# ============================================================

BASE_URL="http://localhost:8080/api"
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

echo "=========================================="
echo "  AML System 端到端 API 测试"
echo "=========================================="

# ==================== 系统模块 ====================
echo ""
echo "[1] 系统健康检查"

RESP=$(curl -s "$BASE_URL/system/health")
check "健康检查" '"code":200' "$RESP"

RESP=$(curl -s "$BASE_URL/system/info")
check "系统信息" '"code":200' "$RESP"

# ==================== 认证模块 ====================
echo ""
echo "[2] 认证模块"

RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')
check "登录成功" '"code":200' "$RESP"
TOKEN=$(echo "$RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "  WARNING: 无法获取Token，后续测试将跳过认证"
else
    echo "  Token: ${TOKEN:0:20}..."
fi

RESP=$(curl -s -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}')
check "错误密码返回失败" '"code":401' "$RESP"

# ==================== KYC 模块 ====================
echo ""
echo "[3] KYC 客户管理"

# 创建客户
RESP=$(curl -s -X POST "$BASE_URL/kyc/customers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{
        "customerType":"INDIVIDUAL",
        "name":"张三",
        "gender":"MALE",
        "nationality":"中国",
        "idType":"IDCARD",
        "idNumber":"110101199001011234",
        "phone":"13800138000",
        "email":"zhangsan@example.com"
    }')
check "创建客户" '"code":200' "$RESP"
CUSTOMER_ID=$(echo "$RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

# 查询客户列表
RESP=$(curl -s "$BASE_URL/kyc/customers/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询客户列表" '"code":200' "$RESP"

# 查询客户详情
if [ -n "$CUSTOMER_ID" ]; then
    RESP=$(curl -s "$BASE_URL/kyc/customers/$CUSTOMER_ID" \
        -H "Authorization: Bearer $TOKEN")
    check "查询客户详情" '"code":200' "$RESP"
fi

# ==================== 交易监测模块 ====================
echo ""
echo "[4] 交易监测"

TXN_NO="TXN$(date +%s)"
RESP=$(curl -s -X POST "$BASE_URL/monitoring/transactions/ingest" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
        \"transactionNo\":\"$TXN_NO\",
        \"customerId\":1,
        \"transactionType\":\"PREMIUM\",
        \"amount\":60000,
        \"currency\":\"CNY\",
        \"paymentMethod\":\"CASH\",
        \"transactionTime\":\"2026-05-01 10:00:00\"
    }")
check "录入交易" '"code":200' "$RESP"

# 查询交易列表
RESP=$(curl -s "$BASE_URL/monitoring/transactions/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询交易列表" '"code":200' "$RESP"

# ==================== 名单筛查模块 ====================
echo ""
echo "[5] 名单筛查"

RESP=$(curl -s -X POST "$BASE_URL/screening/screen" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"customerId":1,"screeningType":"CUSTOMER_ONBOARD"}')
check "触发筛查" '"code":200' "$RESP"

# ==================== 预警模块 ====================
echo ""
echo "[6] 预警管理"

RESP=$(curl -s "$BASE_URL/alerts/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询预警列表" '"code":200' "$RESP"

RESP=$(curl -s "$BASE_URL/alerts/statistics" \
    -H "Authorization: Bearer $TOKEN")
check "预警统计" '"code":200' "$RESP"

# ==================== 案件模块 ====================
echo ""
echo "[7] 案件管理"

RESP=$(curl -s "$BASE_URL/cases/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询案件列表" '"code":200' "$RESP"

# ==================== 报送模块 ====================
echo ""
echo "[8] 监管报送"

RESP=$(curl -s "$BASE_URL/reporting/large-txn/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询大额交易报告" '"code":200' "$RESP"

# ==================== 产品模块 ====================
echo ""
echo "[9] 产品管理"

RESP=$(curl -s "$BASE_URL/products/page?page=1&size=10" \
    -H "Authorization: Bearer $TOKEN")
check "查询产品列表" '"code":200' "$RESP"

# ==================== 系统管理 ====================
echo ""
echo "[10] 系统管理"

RESP=$(curl -s "$BASE_URL/system/dicts" \
    -H "Authorization: Bearer $TOKEN")
check "查询数据字典" '"code":200' "$RESP"

RESP=$(curl -s "$BASE_URL/system/notifications/unread-count" \
    -H "Authorization: Bearer $TOKEN")
check "未读通知数" '"code":200' "$RESP"

# ==================== Swagger ====================
echo ""
echo "[11] API 文档"

RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/doc.html")
check "Swagger文档可访问" "200" "$RESP"

# ==================== 结果汇总 ====================
echo ""
echo "=========================================="
echo "  测试结果: ${GREEN}${PASS} 通过${NC} / ${RED}${FAIL} 失败${NC}"
echo "=========================================="

if [ $FAIL -gt 0 ]; then
    exit 1
fi
