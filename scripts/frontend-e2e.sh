#!/bin/bash
# ============================================================================
# 前端 E2E 测试脚本
# 测试前端页面可访问性和基本交互
# 前提：后端 :8080 + 前端 :5173 均已启动
# ============================================================================

set -e

BASE_URL="${FRONTEND_URL:-${BASE_URL:-http://localhost:5173}}"
API_URL="${API_URL:-http://localhost:8080/api}"
E2E_IP="${E2E_IP:-127.0.0.102}"
E2E_LIMIT_IP="${E2E_LIMIT_IP:-127.0.0.103}"
E2E_RUN_ID="${E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
E2E_PREFIX="${E2E_PREFIX:-E2E}"
PASS=0
FAIL=0
TOTAL=0

# 颜色
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

pass() { ((PASS+=1)); ((TOTAL+=1)); echo -e "  ${GREEN}✓ $1${NC}"; }
fail() {
    ((FAIL+=1))
    ((TOTAL+=1))
    echo -e "  ${RED}✗ $1${NC}"
    if [ -n "${2:-}" ]; then
        printf "    Detail: %.1000s\n" "$(compact_response "$2")"
    fi
}
info() { echo -e "${YELLOW}▶ $1${NC}"; }

# ============================================================================
echo ""
echo "=========================================="
echo "  AML 前端 E2E 测试"
echo "=========================================="
echo "  BASE_URL: $BASE_URL"
echo "  API_URL: $API_URL"
echo "  E2E_RUN_ID: $E2E_RUN_ID"
echo "  E2E_PREFIX: $E2E_PREFIX"
echo ""

# ------ 1. 前端服务可访问 ------
info "1. 前端服务可访问性"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    pass "前端首页可访问 (HTTP $HTTP_CODE)"
else
    fail "前端首页不可访问 (HTTP $HTTP_CODE)" "$BASE_URL"
fi

# ------ 2. 登录页可访问 ------
info "2. 登录页面"
LOGIN_HTML=$(curl -s "$BASE_URL/login" 2>/dev/null || echo "")
if echo "$LOGIN_HTML" | grep -q "vue\|app\|div"; then
    pass "登录页面返回 HTML"
else
    fail "登录页面无内容" "$LOGIN_HTML"
fi

# ------ 3. 后端 API 代理 ------
info "3. Vite 代理 → 后端 API"
HEALTH=$(curl -s "$BASE_URL/api/system/health" 2>/dev/null || echo "")
if echo "$HEALTH" | grep -q '"code":200'; then
    pass "Vite 代理 → /api/system/health 正常"
else
    fail "Vite 代理 → /api/system/health 失败" "$HEALTH"
fi

# ------ 4. 登录 API 测试 ------
info "4. 登录 API"
LOGIN_RESP=$(curl -s -X POST "$API_URL/auth/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: $E2E_IP" \
    -H "X-E2E-Run-Id: $E2E_RUN_ID" \
    -d '{"username":"admin","password":"Aml@Admin#2026!"}' 2>/dev/null || echo "")
if echo "$LOGIN_RESP" | grep -q '"accessToken"'; then
    pass "登录 API 返回 accessToken"
    TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
else
    fail "登录 API 失败" "$LOGIN_RESP"
    TOKEN=""
fi

# ------ 5. Token 刷新 ------
info "5. Token 刷新机制"
if [ -n "$TOKEN" ]; then
    REFRESH_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"refreshToken":"[^"]*"' | cut -d'"' -f4)
    REFRESH_RESP=$(curl -s -X POST "$API_URL/auth/refresh" \
        -H "Content-Type: application/json" \
        -H "X-E2E-Run-Id: $E2E_RUN_ID" \
        -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" 2>/dev/null || echo "")
    if echo "$REFRESH_RESP" | grep -q '"accessToken"'; then
        pass "Token 刷新成功"
    else
        fail "Token 刷新失败" "$REFRESH_RESP"
    fi
else
    fail "跳过（无 Token）"
fi

# ------ 6. 核心页面路由 ------
info "6. 核心页面路由（SPA）"
PAGES=(
    "/dashboard"
    "/kyc"
    "/screening"
    "/monitoring"
    "/alerts"
    "/cases"
    "/reporting"
    "/str-reports"
    "/products"
    "/assessment"
    "/notifications"
    "/system"
)
for page in "${PAGES[@]}"; do
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$page" 2>/dev/null || echo "000")
    if [ "$HTTP" = "200" ]; then
        pass "路由 $page 可访问"
    else
        fail "路由 $page 返回 HTTP $HTTP" "$BASE_URL$page"
    fi
done

# ------ 7. 静态资源 ------
info "7. 静态资源"
# 检查 index.html 中是否包含 Vue 应用挂载点
INDEX_HTML=$(curl -s "$BASE_URL/" 2>/dev/null || echo "")
if echo "$INDEX_HTML" | grep -q 'id="app"'; then
    pass "index.html 包含 #app 挂载点"
else
    fail "index.html 缺少 #app 挂载点" "$INDEX_HTML"
fi

if echo "$INDEX_HTML" | grep -q 'script.*src'; then
    pass "index.html 加载 JS 脚本"
else
    fail "index.html 未加载 JS 脚本" "$INDEX_HTML"
fi

# ------ 8. API 模块覆盖 ------
info "8. 后端 API 模块可用性"
if [ -n "$TOKEN" ]; then
    ENDPOINTS=(
        "/kyc/customers/page?page=1&size=5"
        "/monitoring/transactions/page?page=1&size=5"
        "/screening/results?page=1&size=5"
        "/system/dicts"
        "/system/users/page?page=1&size=5"
        "/alerts/page?page=1&size=5"
        "/cases/page?page=1&size=5"
        "/system/notifications/my?page=1&size=5"
        "/reporting/large-txn/page?page=1&size=5"
    )
    for ep in "${ENDPOINTS[@]}"; do
        RESP=$(curl -s -w "\n%{http_code}" "$API_URL$ep" \
            -H "Authorization: Bearer $TOKEN" 2>/dev/null || echo -e "\n000")
        HTTP=$(echo "$RESP" | tail -1)
        if [ "$HTTP" = "200" ]; then
            pass "API $ep → 200"
        else
            fail "API $ep → HTTP $HTTP" "$RESP"
        fi
    done
else
    fail "跳过（无 Token）"
fi

# ------ 9. 限流验证 ------
info "9. 接口限流验证"
LIMIT_PASS=true
for i in $(seq 1 6); do
    RESP=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: $E2E_LIMIT_IP" \
        -H "X-E2E-Run-Id: $E2E_RUN_ID" \
        -d '{"username":"admin","password":"wrong"}' 2>/dev/null || echo -e "\n000")
    HTTP=$(echo "$RESP" | tail -1)
    if [ "$i" -le 5 ] && [ "$HTTP" != "401" ] && [ "$HTTP" != "400" ]; then
        LIMIT_PASS=false
    fi
    if [ "$i" -gt 5 ] && [ "$HTTP" = "429" ]; then
        pass "第6次请求触发限流 (429)"
        LIMIT_PASS=true
        break
    fi
done
if [ "$LIMIT_PASS" = "false" ]; then
    fail "限流未生效" "第6次错误登录未返回 429，请检查登录限流配置或 E2E_LIMIT_IP 是否被污染。"
fi

# ------ 10. 主题切换（检查 CSS 变量） ------
info "10. 前端构建完整性"
BUILD_DIST="/Users/nathan/Work/Anti-money-Laundering/frontend/dist"
if [ -d "$BUILD_DIST" ]; then
    pass "前端 dist 目录存在"
    if [ -f "$BUILD_DIST/index.html" ]; then
        pass "dist/index.html 存在"
    else
        fail "dist/index.html 不存在" "$BUILD_DIST/index.html"
    fi
else
    info "前端未构建（dev 模式正常）"
fi

# ============================================================================
echo ""
echo "=========================================="
echo -e "  测试结果: ${GREEN}$PASS 通过${NC} / ${RED}$FAIL 失败${NC} / $TOTAL 总计"
echo "=========================================="
echo ""

if [ "$FAIL" -gt 0 ]; then
    exit 1
else
    exit 0
fi
