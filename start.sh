#!/bin/bash
# ============================================================
# AML System - 一键启动脚本
# 启动基础设施 + 编译项目 + 启动应用
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.10/libexec/openjdk.jdk/Contents/Home

echo "=========================================="
echo "  AML System 启动脚本"
echo "=========================================="

# Step 1: 检查 Docker
echo ""
echo "[1/5] 检查 Docker 环境..."
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker 未安装，请先安装: brew install docker colima && colima start"
    exit 1
fi

if ! docker info &> /dev/null 2>&1; then
    echo "Docker 未运行，尝试启动 Colima..."
    colima start --cpu 4 --memory 8 --disk 60 2>/dev/null || {
        echo "ERROR: 无法启动 Docker，请手动运行: colima start"
        exit 1
    }
fi
echo "Docker 环境就绪"

# Step 2: 启动基础设施
echo ""
echo "[2/5] 启动基础设施 (MySQL/Redis/Kafka/ES/MinIO)..."
cd "$PROJECT_DIR/docker"
docker compose -f docker-compose-dev.yml up -d
echo "等待服务启动..."
sleep 15

# Step 3: 检查服务健康
echo ""
echo "[3/5] 检查服务状态..."
echo -n "  MySQL:        " && docker exec aml-mysql-dev mysqladmin ping -h localhost -u root -paml_dev_123 2>/dev/null && echo "OK" || echo "WAITING..."
echo -n "  Redis:        " && docker exec aml-redis-dev redis-cli ping 2>/dev/null && echo "" || echo "WAITING..."
echo -n "  Kafka:        " && docker exec aml-kafka-dev /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null && echo "OK" || echo "WAITING..."
echo -n "  Elasticsearch:" && curl -s http://localhost:9200/_cluster/health | grep -q '"status"' && echo "OK" || echo "WAITING..."
echo -n "  MinIO:        " && curl -s http://localhost:9000/minio/health/live && echo "OK" || echo "WAITING..."

# Step 4: 编译项目
echo ""
echo "[4/5] 编译项目..."
cd "$PROJECT_DIR"
mvn clean compile -DskipTests -q
echo "编译成功"

# Step 5: 启动应用
echo ""
echo "[5/5] 启动 Spring Boot 应用..."
echo "  访问地址: http://localhost:8080/api"
echo "  Swagger:  http://localhost:8080/api/doc.html"
echo "  健康检查: http://localhost:8080/api/system/health"
echo ""
echo "  默认账号: admin / Aml@Admin#2026!"
echo ""
echo "  按 Ctrl+C 停止应用"
echo "=========================================="
mvn spring-boot:run -Dspring-boot.run.profiles=dev
