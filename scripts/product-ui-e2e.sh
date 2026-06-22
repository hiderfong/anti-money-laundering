#!/bin/bash
# ============================================================================
# AML System - 产品管理 UI E2E 闭环脚本
# 前提：后端 :8080 + 前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖产品创建、查询、编辑、风险评估和历史复查。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/product-workflow-e2e.mjs
