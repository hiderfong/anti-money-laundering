#!/bin/bash
# ============================================================================
# AML System - 生产环境部署脚本
# 用法: ./scripts/deploy.sh [version]
# ============================================================================

set -e

VERSION=${1:-latest}
IMAGE_NAME="aml-system"
SERVICE_NAME="aml-app"
COMPOSE_FILE="docker/docker-compose.yml"
ENV_FILE=".env"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

compose() {
    if [ "$COMPOSE_MODE" = "plugin" ]; then
        docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
    else
        docker-compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
    fi
}

echo ""
echo "=========================================="
echo "  AML System 部署"
echo "  版本: $VERSION"
echo "=========================================="
echo ""

# ------ 1. 检查环境 ------
info "检查部署环境..."
command -v docker >/dev/null 2>&1 || error "Docker 未安装"
if docker compose version >/dev/null 2>&1; then
    COMPOSE_MODE="plugin"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_MODE="legacy"
else
    error "Docker Compose 未安装"
fi

if [ ! -f "${ENV_FILE}" ]; then
    error "缺少 ${ENV_FILE}，请复制 .env.example 并填入生产值"
fi

info "执行发布前配置检查..."
bash scripts/prod-readiness-check.sh "${ENV_FILE}"

# ------ 2. 构建镜像 ------
info "构建 Docker 镜像..."
docker build -t ${IMAGE_NAME}:${VERSION} -f docker/Dockerfile .

# ------ 3. 停止旧容器 ------
info "停止旧容器..."
compose down --remove-orphans 2>/dev/null || true

# ------ 4. 启动新容器 ------
info "启动新容器..."
export AML_VERSION=${VERSION}
compose up -d

# ------ 5. 健康检查 ------
info "等待服务启动..."
MAX_RETRIES=30
RETRY_INTERVAL=5
for i in $(seq 1 ${MAX_RETRIES}); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/system/health 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ]; then
        info "服务启动成功！"
        break
    fi
    if [ "$i" -eq "${MAX_RETRIES}" ]; then
        error "服务启动超时，请检查日志: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs ${SERVICE_NAME}"
    fi
    warn "等待服务启动... (${i}/${MAX_RETRIES})"
    sleep ${RETRY_INTERVAL}
done

# ------ 6. 显示状态 ------
echo ""
echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
echo "  应用地址: http://localhost:8080"
echo "  健康检查: http://localhost:8080/api/system/health"
echo "  API 文档: http://localhost:8080/api/doc.html"
echo ""
echo "  查看日志: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs -f ${SERVICE_NAME}"
echo "  停止服务: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} down"
echo ""
