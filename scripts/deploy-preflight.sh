#!/bin/bash
# ============================================================================
# AML System - 生产部署预检
# 用法: bash scripts/deploy-preflight.sh [.env] [--skip-compose]
# ============================================================================

set -euo pipefail

ENV_FILE=".env"
SKIP_COMPOSE=false
COMPOSE_FILE="${COMPOSE_FILE:-docker/docker-compose.yml}"
SERVICE_NAME="${SERVICE_NAME:-aml-app}"
AML_VERSION="${AML_VERSION:-preflight}"
AML_IMAGE_NAME="${AML_IMAGE_NAME:-aml-system}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

for arg in "$@"; do
    case "$arg" in
        --skip-compose)
            SKIP_COMPOSE=true
            ;;
        *)
            ENV_FILE="$arg"
            ;;
    esac
done

compose() {
    if [ "$COMPOSE_MODE" = "plugin" ]; then
        docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
    else
        docker-compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
    fi
}

echo ""
echo "=========================================="
echo "  AML System 部署预检"
echo "  环境文件: ${ENV_FILE}"
echo "=========================================="
echo ""

if [ ! -f "${ENV_FILE}" ]; then
    error "环境文件不存在: ${ENV_FILE}"
fi

info "执行生产配置 readiness 检查..."
bash scripts/prod-readiness-check.sh "${ENV_FILE}"

set -a
# shellcheck disable=SC1090
. "${ENV_FILE}"
set +a

export AML_VERSION
export AML_IMAGE_NAME

if [ "${SKIP_COMPOSE}" = "true" ]; then
    warn "已跳过 Docker Compose 配置渲染"
else
    info "检查 Docker 与 Docker Compose..."
    command -v docker >/dev/null 2>&1 || error "Docker 未安装"
    if docker compose version >/dev/null 2>&1; then
        COMPOSE_MODE="plugin"
    elif command -v docker-compose >/dev/null 2>&1; then
        COMPOSE_MODE="legacy"
    else
        error "Docker Compose 未安装"
    fi

    info "渲染并校验 Docker Compose 配置..."
    compose config -q

    info "确认 ${SERVICE_NAME} 服务可被 Compose 解析..."
    compose config --services | grep -qx "${SERVICE_NAME}" || error "Compose 中找不到服务: ${SERVICE_NAME}"
fi

echo ""
echo "=========================================="
echo "  预检通过"
echo "=========================================="
echo ""
echo "  建议下一步: bash scripts/deploy.sh <version>"
echo ""
