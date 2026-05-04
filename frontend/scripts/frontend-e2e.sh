#!/bin/bash
# 前端E2E可用性测试脚本
# 用法: bash scripts/frontend-e2e.sh

set -e

FRONTEND_URL="http://localhost:5173"
PASSED=0
FAILED=0

log_pass() {
  echo "[PASS] $1"
  PASSED=$((PASSED + 1))
}

log_fail() {
  echo "[FAIL] $1"
  FAILED=$((FAILED + 1))
}

echo "========================================"
echo "  前端 E2E 可用性测试"
echo "========================================"
echo ""

# 1. 检查前端服务是否运行
echo "--- 基础连通性 ---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
  log_pass "前端服务可访问 (HTTP $HTTP_CODE)"
else
  log_fail "前端服务不可访问 (HTTP $HTTP_CODE)"
  echo "请先启动前端服务: cd frontend && npm run dev"
  exit 1
fi

# 2. 检查登录页面可访问
echo ""
echo "--- 页面可访问性 ---"
BODY=$(curl -s "$FRONTEND_URL/login" 2>/dev/null)
if echo "$BODY" | grep -q "app" >/dev/null 2>&1; then
  log_pass "登录页面可访问"
else
  # SPA 可能返回 index.html，检查是否包含 Vue app 挂载点
  if echo "$BODY" | grep -q "<div id=\"app\"" >/dev/null 2>&1; then
    log_pass "登录页面可访问 (SPA index.html)"
  else
    log_fail "登录页面不可访问"
  fi
fi

# 3. 检查 index.html 包含必要元素
if echo "$BODY" | grep -q "<title>" >/dev/null 2>&1; then
  log_pass "页面包含 title 标签"
else
  log_fail "页面缺少 title 标签"
fi

if echo "$BODY" | grep -q "src=" >/dev/null 2>&1; then
  log_pass "页面引用了 JS 资源"
else
  log_fail "页面未引用 JS 资源"
fi

# 4. 检查静态资源加载
echo ""
echo "--- 静态资源 ---"
# 获取 JS 文件路径
JS_FILE=$(echo "$BODY" | grep -oE 'src="/assets/[^"]+\.js"' | head -1 | sed 's/src="//;s/"//')
if [ -n "$JS_FILE" ]; then
  JS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL$JS_FILE" 2>/dev/null || echo "000")
  if [ "$JS_CODE" = "200" ]; then
    log_pass "JS 资源加载正常 ($JS_FILE)"
  else
    log_fail "JS 资源加载失败 ($JS_FILE, HTTP $JS_CODE)"
  fi
else
  log_fail "未找到 JS 资源引用"
fi

# 检查 CSS 文件
CSS_FILE=$(echo "$BODY" | grep -oE 'href="/assets/[^"]+\.css"' | head -1 | sed 's/href="//;s/"//')
if [ -n "$CSS_FILE" ]; then
  CSS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL$CSS_FILE" 2>/dev/null || echo "000")
  if [ "$CSS_CODE" = "200" ]; then
    log_pass "CSS 资源加载正常 ($CSS_FILE)"
  else
    log_fail "CSS 资源加载失败 ($CSS_FILE, HTTP $CSS_CODE)"
  fi
else
  log_fail "未找到 CSS 资源引用"
fi

# 5. 检查 API 代理
echo ""
echo "--- API 代理 ---"
API_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/api/health" 2>/dev/null || echo "000")
if [ "$API_CODE" = "000" ]; then
  log_fail "API 代理不可达 (连接失败)"
elif [ "$API_CODE" = "502" ] || [ "$API_CODE" = "503" ]; then
  log_fail "API 代理转发失败 (后端未运行, HTTP $API_CODE)"
else
  log_pass "API 代理工作正常 (HTTP $API_CODE)"
fi

# 6. 检查 SPA 路由（返回 index.html）
echo ""
echo "--- SPA 路由 ---"
SPA_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/dashboard" 2>/dev/null || echo "000")
if [ "$SPA_CODE" = "200" ]; then
  log_pass "SPA 路由正常 (/dashboard 返回 200)"
else
  log_fail "SPA 路由异常 (/dashboard 返回 HTTP $SPA_CODE)"
fi

SPA_404_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/nonexistent-page" 2>/dev/null || echo "000")
if [ "$SPA_404_CODE" = "200" ]; then
  log_pass "SPA 404 路由正常 (/nonexistent-page 返回 index.html)"
else
  log_fail "SPA 404 路由异常 (/nonexistent-page 返回 HTTP $SPA_404_CODE)"
fi

# 汇总
echo ""
echo "========================================"
echo "  结果: $PASSED 通过, $FAILED 失败"
echo "========================================"
if [ $FAILED -gt 0 ]; then
  exit 1
fi
exit 0
