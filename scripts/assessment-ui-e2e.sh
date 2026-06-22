#!/bin/bash
# ============================================================================
# AML System - 风险自评估 UI E2E 闭环脚本
# 前提：后端 :8080 + 前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖自评估创建、指标评分、完成审批和关联整改任务。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/assessment-workflow-e2e.mjs
