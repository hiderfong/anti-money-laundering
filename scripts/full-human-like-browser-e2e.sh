#!/bin/bash
# ============================================================================
# AML System - 全量人工仿真浏览器 E2E 巡检脚本
# 前提：后端 API 与前端 FRONTEND_URL 均已启动。
# 使用 Playwright 驱动真实浏览器，覆盖登录、核心页面巡检、关键业务数据、
# 客户/交易/预警图谱链路和截图报告。
# ============================================================================

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/frontend"
node scripts/full-human-like-browser-e2e.mjs
