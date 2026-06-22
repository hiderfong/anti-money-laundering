#!/bin/bash
# ============================================================================
# AML System - 通知中心案件通知 UI E2E 闭环脚本
# 前提：后端 :8080 + 前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖案件通知生成、详情查看、已读状态和案件跳转。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/notification-workflow-e2e.mjs
