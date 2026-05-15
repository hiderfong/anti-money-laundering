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
E2E_FREEZE_DOC_NO="${E2E_FREEZE_DOC_NO:-${E2E_PREFIX}_FSD_${E2E_RUN_ID}}"
E2E_INVESTIGATION_DOC_NO="${E2E_INVESTIGATION_DOC_NO:-${E2E_PREFIX}_IRQ_DOC_${E2E_RUN_ID}}"

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

auth_put() {
    curl -sS -X PUT "$1" \
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
    -d '{"username":"admin","password":"admin123"}')
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

if [ "${RUN_GRAPH_E2E:-false}" = "true" ] && [ -n "$CUSTOMER_ID" ]; then
    echo ""
    echo "[4.1] 图分析（Neo4j 可选）"

    RESP=$(auth_get "$BASE_URL/monitoring/graph/ring-detection?customerId=$CUSTOMER_ID")
    check "环形交易检测" '"code":200' "$RESP"

    RESP=$(auth_get "$BASE_URL/monitoring/graph/multi-layer-transfer?customerId=$CUSTOMER_ID&maxDepth=3")
    check "多层转账追踪" '"code":200' "$RESP"

    RESP=$(auth_get "$BASE_URL/monitoring/graph/shared-accounts?customerId=$CUSTOMER_ID")
    check "共同账户检测" '"code":200' "$RESP"

    RESP=$(auth_get "$BASE_URL/monitoring/graph/network-density?customerId=$CUSTOMER_ID&densityThreshold=10")
    check "异常网络密度检测" '"code":200' "$RESP"
fi

# ==================== 名单筛查模块 ====================
echo ""
echo "[5] 名单筛查"

RESP=$(auth_post "$BASE_URL/screening/screen" \
    "{\"customerId\":${CUSTOMER_ID:-1},\"screeningType\":\"SANCTIONS\"}")
check "触发筛查" '"code":200' "$RESP"

# ==================== 特别预防模块 ====================
echo ""
echo "[6] 特别预防"

RESP=$(auth_get "$BASE_URL/special-prevention/overview")
check "特别预防概览" '"code":200' "$RESP"

RESP=$(auth_post "$BASE_URL/special-prevention/watchlist-update-jobs" '{"updateMode":"MANUAL"}')
check "创建名单更新任务" '"code":200' "$RESP"

RESP=$(auth_post "$BASE_URL/special-prevention/retrospective-jobs" "{
    \"jobName\":\"${E2E_PREFIX}回溯筛查_${E2E_RUN_ID}\",
    \"scopeType\":\"ALL_CUSTOMERS\",
    \"remark\":\"${E2E_PREFIX} API E2E\"
}")
check "创建回溯筛查任务" '"code":200' "$RESP"

if [ -n "$CUSTOMER_ID" ]; then
    RESP=$(auth_post "$BASE_URL/special-prevention/measures" "{
        \"customerId\":${CUSTOMER_ID},
        \"measureType\":\"TRANSACTION_LIMIT\",
        \"triggerType\":\"WATCHLIST_HIT\",
        \"controlLevel\":\"HIGH\",
        \"measureContent\":\"${E2E_PREFIX}限制大额现金交易_${E2E_RUN_ID}\",
        \"startDate\":\"2026-05-14\",
        \"endDate\":\"2026-06-14\",
        \"decisionReason\":\"${E2E_PREFIX} API E2E\"
    }")
    check "创建特别预防措施" '"code":200' "$RESP"

    RESP=$(auth_post "$BASE_URL/special-prevention/freeze-records" "{
        \"customerId\":${CUSTOMER_ID},
        \"authorityName\":\"${E2E_PREFIX}测试机关\",
        \"documentNo\":\"${E2E_FREEZE_DOC_NO}\",
        \"actionType\":\"QUERY\",
        \"amount\":1000,
        \"currency\":\"CNY\",
        \"effectiveDate\":\"2026-05-14\",
        \"expiryDate\":\"2026-06-14\",
        \"handler\":\"admin\",
        \"remark\":\"${E2E_PREFIX} API E2E\"
    }")
    check "创建查冻扣记录" '"code":200' "$RESP"
fi

RESP=$(auth_get "$BASE_URL/special-prevention/measures?page=1&size=10")
check "查询特别预防措施" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/special-prevention/freeze-records?page=1&size=10")
check "查询查冻扣记录" '"code":200' "$RESP"

# ==================== 预警模块 ====================
echo ""
echo "[7] 预警管理"

RESP=$(auth_get "$BASE_URL/alerts/page?page=1&size=10")
check "查询预警列表" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/alerts/statistics")
check "预警统计" '"code":200' "$RESP"

# ==================== 案件模块 ====================
echo ""
echo "[8] 案件管理"

RESP=$(auth_get "$BASE_URL/cases/page?page=1&size=10")
check "查询案件列表" '"code":200' "$RESP"

# ==================== 调查协查 ====================
echo ""
echo "[9] 调查协查"

RESP=$(auth_get "$BASE_URL/investigations/overview")
check "调查协查概览" '"code":200' "$RESP"

if [ -n "$CUSTOMER_ID" ]; then
    RESP=$(auth_post "$BASE_URL/investigations" "{
        \"authorityName\":\"${E2E_PREFIX}测试机关\",
        \"requestType\":\"ASSIST_INVESTIGATION\",
        \"documentNo\":\"${E2E_INVESTIGATION_DOC_NO}\",
        \"customerId\":${CUSTOMER_ID},
        \"priority\":\"HIGH\",
        \"receivedDate\":\"2026-05-14\",
        \"dueDate\":\"2026-06-14\",
        \"handler\":\"admin\",
        \"summary\":\"${E2E_PREFIX}调查协查接口验证_${E2E_RUN_ID}\"
    }")
    check "创建调查协查请求" '"code":200' "$RESP"
    INVESTIGATION_ID=$(echo "$RESP" | jq -r '.data.id // empty' 2>/dev/null)
fi

if [ -n "${INVESTIGATION_ID:-}" ]; then
    RESP=$(auth_post "$BASE_URL/investigations/$INVESTIGATION_ID/actions" "{
        \"actionType\":\"REVIEW\",
        \"actionContent\":\"${E2E_PREFIX}查阅客户尽调和交易材料\",
        \"actionResult\":\"材料已整理\",
        \"operator\":\"admin\",
        \"attachmentRef\":\"${E2E_PREFIX}-archive-${E2E_RUN_ID}\"
    }")
    check "登记调查协查动作" '"code":200' "$RESP"

    RESP=$(auth_put "$BASE_URL/investigations/$INVESTIGATION_ID/status" "{
        \"status\":\"RESPONDED\",
        \"responseSummary\":\"${E2E_PREFIX}调查协查已回复\"
    }")
    check "更新调查协查状态" '"code":200' "$RESP"

    RESP=$(auth_get "$BASE_URL/investigations/$INVESTIGATION_ID/actions?page=1&size=10")
    check "查询调查协查动作" '"code":200' "$RESP"
fi

RESP=$(auth_get "$BASE_URL/investigations?page=1&size=10")
check "查询调查协查请求" '"code":200' "$RESP"

# ==================== 报送模块 ====================
echo ""
echo "[10] 监管报送"

RESP=$(auth_get "$BASE_URL/reporting/large-txn/page?page=1&size=10")
check "查询大额交易报告" '"code":200' "$RESP"

# ==================== 产品模块 ====================
echo ""
echo "[11] 产品管理"

RESP=$(auth_get "$BASE_URL/products/page?page=1&size=10")
check "查询产品列表" '"code":200' "$RESP"

# ==================== 系统管理 ====================
echo ""
echo "[12] 系统管理"

RESP=$(auth_get "$BASE_URL/system/dicts")
check "查询数据字典" '"code":200' "$RESP"

RESP=$(auth_get "$BASE_URL/system/notifications/unread-count")
check "未读通知数" '"code":200' "$RESP"

# ==================== 整改中心 ====================
echo ""
echo "[13] 整改中心"

RESP=$(auth_post "$BASE_URL/rectifications" "{
    \"sourceType\":\"INTERNAL_CHECK\",
    \"issueDescription\":\"${E2E_PREFIX}整改中心接口验证_${E2E_RUN_ID}\",
    \"issueCategory\":\"流程缺陷\",
    \"severity\":\"MEDIUM\",
    \"responsibleDept\":\"合规部\",
    \"responsiblePerson\":\"admin\",
    \"deadline\":\"2026-06-14\"
}")
check "创建整改任务" '"code":200' "$RESP"
RECTIFICATION_ID=$(echo "$RESP" | jq -r '.data.id // empty' 2>/dev/null)

if [ -n "$RECTIFICATION_ID" ]; then
    RESP=$(auth_put "$BASE_URL/rectifications/$RECTIFICATION_ID/progress" '{"progressPercent":100,"completionEvidence":"E2E整改证据","status":"COMPLETED"}')
    check "更新整改进度" '"code":200' "$RESP"

    RESP=$(auth_post "$BASE_URL/rectifications/$RECTIFICATION_ID/verify" '{"verificationStatus":"PASSED","verifyResult":"E2E验证通过"}')
    check "验证整改任务" '"code":200' "$RESP"
fi

RESP=$(auth_get "$BASE_URL/rectifications?page=1&size=10")
check "查询整改任务" '"code":200' "$RESP"

# ==================== Swagger ====================
echo ""
echo "[14] API 文档"

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
