#!/bin/bash
# ============================================================================
# AML System - systemd 服务安装/更新脚本
#
# 用法:
#   sudo bash scripts/install-systemd-service.sh
#
# 可选环境变量:
#   AML_WORK_DIR=/work/aml
#   AML_ENV_FILE=/work/aml/.env.production
#   AML_JAR_PATH=/work/aml/target/aml-system-1.0.0-SNAPSHOT.jar
#   AML_SERVICE_NAME=aml-system
#   AML_SERVICE_USER=root
#   AML_SERVICE_GROUP=root
#   JAVA_BIN=/usr/bin/java
#   HEALTH_URL=http://127.0.0.1:8080/api/system/health
#   INSTALL_RESTART=true|false
#   STOP_LEGACY_PROCESSES=true|false
# ============================================================================

set -euo pipefail

AML_WORK_DIR="${AML_WORK_DIR:-/work/aml}"
AML_ENV_FILE="${AML_ENV_FILE:-${AML_WORK_DIR}/.env.production}"
AML_JAR_PATH="${AML_JAR_PATH:-${AML_WORK_DIR}/target/aml-system-1.0.0-SNAPSHOT.jar}"
AML_SERVICE_NAME="${AML_SERVICE_NAME:-aml-system}"
AML_SERVICE_USER="${AML_SERVICE_USER:-root}"
AML_SERVICE_GROUP="${AML_SERVICE_GROUP:-${AML_SERVICE_USER}}"
JAVA_BIN="${JAVA_BIN:-$(command -v java || true)}"
INSTALL_RESTART="${INSTALL_RESTART:-true}"
STOP_LEGACY_PROCESSES="${STOP_LEGACY_PROCESSES:-true}"
SYSTEMD_DIR="${SYSTEMD_DIR:-/etc/systemd/system}"
TEMPLATE_PATH="${TEMPLATE_PATH:-deploy/systemd/aml-system.service.template}"
SERVICE_PATH="${SYSTEMD_DIR}/${AML_SERVICE_NAME}.service"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1" >&2; exit 1; }

require_root() {
    if [ "$(id -u)" -ne 0 ]; then
        error "请使用 root 或 sudo 执行该脚本"
    fi
}

require_file() {
    local path="$1"
    local name="$2"
    if [ ! -f "$path" ]; then
        error "缺少 ${name}: ${path}"
    fi
}

read_env_value() {
    local key="$1"
    local fallback="$2"
    local value
    value="$(awk -F= -v key="$key" '
        $0 !~ /^[[:space:]]*#/ && $1 == key {
            sub(/^[^=]*=/, "", $0)
            print $0
            exit
        }
    ' "$AML_ENV_FILE" 2>/dev/null || true)"
    if [ -n "$value" ]; then
        printf '%s' "$value"
    else
        printf '%s' "$fallback"
    fi
}

render_service() {
    local tmp_file server_address server_port
    server_address="$(read_env_value AML_APP_BIND_ADDR 127.0.0.1)"
    server_port="$(read_env_value AML_APP_PORT 8080)"
    tmp_file="$(mktemp)"
    sed \
        -e "s|@AML_SERVICE_USER@|${AML_SERVICE_USER}|g" \
        -e "s|@AML_SERVICE_GROUP@|${AML_SERVICE_GROUP}|g" \
        -e "s|@AML_WORK_DIR@|${AML_WORK_DIR}|g" \
        -e "s|@AML_ENV_FILE@|${AML_ENV_FILE}|g" \
        -e "s|@AML_JAR_PATH@|${AML_JAR_PATH}|g" \
        -e "s|@SERVER_ADDRESS@|${server_address}|g" \
        -e "s|@SERVER_PORT@|${server_port}|g" \
        -e "s|@JAVA_BIN@|${JAVA_BIN}|g" \
        "$TEMPLATE_PATH" > "$tmp_file"

    install -m 0644 "$tmp_file" "$SERVICE_PATH"
    rm -f "$tmp_file"
}

stop_legacy_processes() {
    if [ "$STOP_LEGACY_PROCESSES" != "true" ]; then
        warn "STOP_LEGACY_PROCESSES=false，跳过旧 Java 进程清理"
        return 0
    fi

    local current_main_pid jar_base pids pid
    current_main_pid="$(systemctl show -p MainPID --value "$AML_SERVICE_NAME" 2>/dev/null || true)"
    current_main_pid="${current_main_pid:-0}"
    jar_base="$(basename "$AML_JAR_PATH")"
    pids="$(pgrep -f "java .*${jar_base}" 2>/dev/null || true)"

    if [ -z "$pids" ]; then
        return 0
    fi

    for pid in $pids; do
        if [ "$pid" = "$$" ] || [ "$pid" = "$current_main_pid" ] || [ "$pid" = "0" ]; then
            continue
        fi
        warn "停止旧后端 Java 进程 PID=${pid}"
        kill "$pid" 2>/dev/null || true
    done

    sleep 5
    pids="$(pgrep -f "java .*${jar_base}" 2>/dev/null || true)"
    for pid in $pids; do
        if [ "$pid" = "$$" ] || [ "$pid" = "$current_main_pid" ] || [ "$pid" = "0" ]; then
            continue
        fi
        warn "旧后端 Java 进程未正常退出，强制停止 PID=${pid}"
        kill -9 "$pid" 2>/dev/null || true
    done
}

wait_for_health() {
    local app_port health_url
    app_port="$(read_env_value AML_APP_PORT 8080)"
    health_url="${HEALTH_URL:-http://127.0.0.1:${app_port}/api/system/health}"

    info "等待健康检查通过: ${health_url}"
    for i in $(seq 1 30); do
        if curl -fsS "$health_url" >/dev/null 2>&1; then
            info "健康检查通过"
            return 0
        fi
        sleep 3
    done

    systemctl status "$AML_SERVICE_NAME" --no-pager || true
    error "服务启动后健康检查未通过"
}

main() {
    require_root
    require_file "$TEMPLATE_PATH" "systemd 模板"
    require_file "$AML_ENV_FILE" "生产环境配置"
    require_file "$AML_JAR_PATH" "后端 JAR"
    if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
        error "未找到可执行 java，请设置 JAVA_BIN"
    fi

    mkdir -p "${AML_WORK_DIR}/logs"

    info "安装 systemd 服务: ${SERVICE_PATH}"
    render_service
    systemctl daemon-reload
    systemctl enable "$AML_SERVICE_NAME"

    if [ "$INSTALL_RESTART" = "true" ]; then
        stop_legacy_processes
        info "重启服务: ${AML_SERVICE_NAME}"
        systemctl restart "$AML_SERVICE_NAME"
        wait_for_health
    else
        warn "INSTALL_RESTART=false，已安装但未重启服务"
    fi

    systemctl --no-pager --full status "$AML_SERVICE_NAME" || true
}

main "$@"
