#!/bin/bash
# ============================================================================
# AML System - 整改中心 UI E2E 闭环脚本
# 前提：后端 :8080 + 前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖整改任务创建、查询、进度更新和验证闭环。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/rectification-workflow-e2e.mjs
