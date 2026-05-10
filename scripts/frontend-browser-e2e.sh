#!/bin/bash
# ============================================================================
# AML System - 前端浏览器 E2E 回归脚本
# 前提：后端 :8080 + 前端 :5173 均已启动。
# 该脚本使用 Playwright 驱动真实浏览器，覆盖登录、刷新、核心页面渲染、
# console/pageerror 和 /api/auth/me roles/permissions 契约。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/frontend-browser-e2e.mjs
