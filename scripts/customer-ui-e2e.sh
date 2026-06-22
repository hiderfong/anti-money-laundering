#!/bin/bash
# ============================================================================
# AML System - 客户管理 UI E2E 闭环脚本
# 前提：后端 :8080 + 前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖新增客户、搜索、详情、客户画像、
# 客户关系图谱入口和关系图谱 API 契约。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/customer-workflow-e2e.mjs
