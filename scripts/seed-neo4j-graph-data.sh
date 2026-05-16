#!/bin/bash
# ============================================================================
# AML System - Neo4j 交易图谱数据同步脚本
#
# 从 MySQL t_transaction/t_customer 读取交易与客户数据，并写入 Neo4j：
#   (:Customer)-[:OWNS]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)<-[:OWNS]-(:Customer)
#   (:Customer)-[:TRANSFERS_TO {transactionId, amount}]->(:Customer)
#
# 默认 dry-run；传入 --execute 才写入 Neo4j。
# ============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-aml_system}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-aml_dev_123}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"

NEO4J_CONTAINER="${NEO4J_CONTAINER:-aml-neo4j-dev}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-aml_neo4j_123}"

SOURCE_SYSTEM="E2E-GRAPH-SEED"
CUSTOMER_ID=""
LIMIT=1000
EXECUTE=false
VERIFY=false
ALL=false
CYPHER_ONLY=false
BATCH_ID="$(date +%Y%m%d%H%M%S)"

usage() {
    cat <<'EOF'
Usage:
  scripts/seed-neo4j-graph-data.sh [--execute] [--verify]

Options:
  --execute                 写入 Neo4j；不传时只生成 dry-run 预览。
  --verify                  输出 Neo4j 中本批次写入的数据统计。
  --all                     不按 source_system 过滤，同步全部交易。
  --source-system VALUE     只同步指定来源系统，默认 E2E-GRAPH-SEED。
  --customer-id ID          只同步指定客户作为发起方的交易。
  --limit N                 最大同步笔数，默认 1000，最大 5000。
  --cypher-only             只生成 TSV/Cypher 文件，不调用 Docker/Neo4j。
  --mysql-container NAME    使用 MySQL Docker 容器执行查询，例如 aml-mysql-dev。
  -h, --help                显示帮助。

Environment:
  DB_HOST                   默认 127.0.0.1
  DB_PORT                   默认 3306
  DB_NAME                   默认 aml_system
  DB_USER                   默认 root
  DB_PASSWORD               默认 aml_dev_123
  MYSQL_CONTAINER           设置后使用该容器内 mysql 客户端，绕开本机 mysql 插件兼容问题
  NEO4J_CONTAINER           默认 aml-neo4j-dev
  NEO4J_USER                默认 neo4j
  NEO4J_PASSWORD            默认 aml_neo4j_123

Typical:
  docker compose -f docker/docker-compose-dev.yml up -d neo4j
  scripts/seed-neo4j-graph-data.sh --execute --verify
  # 后端需以 aml.neo4j.enabled=true 且不启用 no-es/no-redis 这类排除 Neo4j 的 profile 启动。
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --execute)
            EXECUTE=true
            shift
            ;;
        --verify)
            VERIFY=true
            shift
            ;;
        --all)
            ALL=true
            shift
            ;;
        --source-system)
            SOURCE_SYSTEM="${2:-}"
            shift 2
            ;;
        --customer-id)
            CUSTOMER_ID="${2:-}"
            shift 2
            ;;
        --limit)
            LIMIT="${2:-1000}"
            shift 2
            ;;
        --cypher-only)
            CYPHER_ONLY=true
            shift
            ;;
        --mysql-container)
            MYSQL_CONTAINER="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

if ! [[ "$LIMIT" =~ ^[0-9]+$ ]]; then
    echo "--limit must be a positive integer." >&2
    exit 1
fi
if [ "$LIMIT" -lt 1 ]; then
    LIMIT=1
fi
if [ "$LIMIT" -gt 5000 ]; then
    LIMIT=5000
fi

if [ -z "$MYSQL_CONTAINER" ] && ! command -v mysql >/dev/null 2>&1; then
    echo "mysql command not found. Install MySQL client first." >&2
    exit 1
fi

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

MYSQL_ARGS=(
    -h "$DB_HOST"
    -P "$DB_PORT"
    -u "$DB_USER"
    --database "$DB_NAME"
    --batch
    --raw
    --skip-column-names
)

if [ -n "$DB_PASSWORD" ]; then
    export MYSQL_PWD="$DB_PASSWORD"
fi

run_mysql() {
    local sql="$1"
    if [ -n "$MYSQL_CONTAINER" ]; then
        if ! command -v docker >/dev/null 2>&1; then
            echo "docker command not found, cannot use MYSQL_CONTAINER=${MYSQL_CONTAINER}." >&2
            return 1
        fi
        docker exec -i -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER" mysql \
            -h 127.0.0.1 \
            -P 3306 \
            -u "$DB_USER" \
            --database "$DB_NAME" \
            --batch \
            --raw \
            --skip-column-names <<<"$sql"
    else
        mysql "${MYSQL_ARGS[@]}" <<<"$sql"
    fi
}

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

IMPORT_NAME="aml-graph-transactions-${BATCH_ID}.tsv"
TSV_FILE="$WORK_DIR/$IMPORT_NAME"
CYPHER_FILE="$WORK_DIR/seed-neo4j-${BATCH_ID}.cypher"
SOURCE_SQL="$(sql_escape "$SOURCE_SYSTEM")"

ALL_SQL=0
if [ "$ALL" = true ] || [ -z "$SOURCE_SYSTEM" ]; then
    ALL_SQL=1
fi

CUSTOMER_FILTER_SQL="1=1"
if [ -n "$CUSTOMER_ID" ]; then
    if ! [[ "$CUSTOMER_ID" =~ ^[0-9]+$ ]]; then
        echo "--customer-id must be a numeric customer id." >&2
        exit 1
    fi
    CUSTOMER_FILTER_SQL="t.customer_id = ${CUSTOMER_ID}"
fi

SANITIZE_PREFIX="REPLACE(REPLACE(REPLACE("
SANITIZE_SUFFIX=", CHAR(9), ' '), CHAR(10), ' '), CHAR(13), ' ')"

read -r -d '' EXPORT_SQL <<SQL || true
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET collation_connection = 'utf8mb4_unicode_ci';
SET @source_system := '${SOURCE_SQL}';

SELECT 'id', 'transactionNo', 'customerId', 'customerName', 'sourceAccount', 'transactionType',
       'amount', 'currency', 'paymentMethod', 'channel', 'counterpartyName', 'counterpartyId',
       'counterpartyAccount', 'counterpartyBank', 'isCrossBorder', 'transactionTime', 'sourceSystem'
UNION ALL
SELECT * FROM (
  SELECT
    CAST(t.id AS CHAR) AS id,
    ${SANITIZE_PREFIX}COALESCE(t.transaction_no, '')${SANITIZE_SUFFIX} AS transactionNo,
    CAST(t.customer_id AS CHAR) AS customerId,
    ${SANITIZE_PREFIX}COALESCE(c.name, CONCAT('客户 ', t.customer_id))${SANITIZE_SUFFIX} AS customerName,
    CONCAT('CUST_', t.customer_id) AS sourceAccount,
    ${SANITIZE_PREFIX}COALESCE(t.transaction_type, '')${SANITIZE_SUFFIX} AS transactionType,
    CAST(COALESCE(t.amount, 0) AS CHAR) AS amount,
    ${SANITIZE_PREFIX}COALESCE(t.currency, 'CNY')${SANITIZE_SUFFIX} AS currency,
    ${SANITIZE_PREFIX}COALESCE(t.payment_method, '')${SANITIZE_SUFFIX} AS paymentMethod,
    ${SANITIZE_PREFIX}COALESCE(t.channel, '')${SANITIZE_SUFFIX} AS channel,
    ${SANITIZE_PREFIX}COALESCE(t.counterparty_name, CONCAT('交易对手 ', t.id))${SANITIZE_SUFFIX} AS counterpartyName,
    CAST(COALESCE(cp_tx.id, cp.id, CRC32(COALESCE(t.counterparty_name, CONCAT('COUNTERPARTY_', t.id)))) AS CHAR) AS counterpartyId,
    ${SANITIZE_PREFIX}COALESCE(NULLIF(t.counterparty_account, ''), CONCAT('UNKNOWN_', t.id))${SANITIZE_SUFFIX} AS counterpartyAccount,
    ${SANITIZE_PREFIX}COALESCE(t.counterparty_bank, '')${SANITIZE_SUFFIX} AS counterpartyBank,
    CAST(COALESCE(t.is_cross_border, 0) AS CHAR) AS isCrossBorder,
    DATE_FORMAT(COALESCE(t.transaction_time, t.created_time, NOW()), '%Y-%m-%d %H:%i:%s') AS transactionTime,
    ${SANITIZE_PREFIX}COALESCE(t.source_system, '')${SANITIZE_SUFFIX} AS sourceSystem
  FROM t_transaction t
  LEFT JOIN t_customer c ON c.id = t.customer_id
  LEFT JOIN (
    SELECT c2.name AS party_name, MAX(c2.id) AS id
    FROM t_transaction tx
    JOIN t_customer c2 ON c2.id = tx.customer_id
    WHERE tx.customer_id IS NOT NULL
      AND (
        ${ALL_SQL} = 1
        OR tx.source_system = @source_system
        OR LOCATE('复杂关联链路', COALESCE(tx.remark, '')) > 0
        OR LOCATE('GRAPH-SEED', COALESCE(tx.source_system, '')) > 0
      )
    GROUP BY c2.name
  ) cp_tx ON cp_tx.party_name = t.counterparty_name
  LEFT JOIN (
    SELECT party_name, MAX(id) AS id
    FROM (
      SELECT name AS party_name, id FROM t_customer WHERE name IS NOT NULL AND name <> ''
      UNION ALL
      SELECT name_en AS party_name, id FROM t_customer WHERE name_en IS NOT NULL AND name_en <> ''
    ) customer_names
    GROUP BY party_name
  ) cp ON cp.party_name = t.counterparty_name
  WHERE t.customer_id IS NOT NULL
    AND (${CUSTOMER_FILTER_SQL})
    AND (
      ${ALL_SQL} = 1
      OR t.source_system = @source_system
      OR LOCATE('复杂关联链路', COALESCE(t.remark, '')) > 0
      OR LOCATE('GRAPH-SEED', COALESCE(t.source_system, '')) > 0
    )
  ORDER BY t.transaction_time ASC, t.id ASC
  LIMIT ${LIMIT}
) graph_rows;
SQL

if ! run_mysql "$EXPORT_SQL" > "$TSV_FILE"; then
    if [ -z "$MYSQL_CONTAINER" ] && command -v docker >/dev/null 2>&1 \
        && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "aml-mysql-dev"; then
        echo "Local mysql failed; retrying with Docker container aml-mysql-dev..." >&2
        MYSQL_CONTAINER="aml-mysql-dev"
        run_mysql "$EXPORT_SQL" > "$TSV_FILE"
    else
        echo "Failed to export transactions from MySQL. If local mysql reports mysql_native_password plugin errors, retry with:" >&2
        echo "  MYSQL_CONTAINER=aml-mysql-dev $0 --execute --verify" >&2
        exit 1
    fi
fi
ROW_COUNT="$(tail -n +2 "$TSV_FILE" | wc -l | tr -d ' ')"

cat > "$CYPHER_FILE" <<CYPHER
CREATE CONSTRAINT aml_customer_id IF NOT EXISTS FOR (c:Customer) REQUIRE c.customerId IS UNIQUE;
CREATE CONSTRAINT aml_account_no IF NOT EXISTS FOR (a:Account) REQUIRE a.accountNo IS UNIQUE;
CREATE CONSTRAINT aml_transaction_id IF NOT EXISTS FOR (t:Transaction) REQUIRE t.transactionId IS UNIQUE;

LOAD CSV WITH HEADERS FROM 'file:///${IMPORT_NAME}' AS row FIELDTERMINATOR '\t'
WITH row WHERE row.id IS NOT NULL AND row.id <> ''
MATCH ()-[flow:TRANSFERS_TO {transactionId: toInteger(row.id)}]->()
DELETE flow;

LOAD CSV WITH HEADERS FROM 'file:///${IMPORT_NAME}' AS row FIELDTERMINATOR '\t'
WITH row WHERE row.id IS NOT NULL AND row.id <> ''
WITH row,
     CASE WHEN row.counterpartyAccount IS NULL OR row.counterpartyAccount = '' THEN 'UNKNOWN_' + row.id ELSE row.counterpartyAccount END AS dstAccountNo
MERGE (src:Customer {customerId: toInteger(row.customerId)})
  SET src.name = row.customerName,
      src.seedSource = 'mysql-transaction-seed',
      src.seedBatch = '${BATCH_ID}'
MERGE (srcAcct:Account {accountNo: row.sourceAccount})
  SET srcAcct.bank = '客户自有账户',
      srcAcct.seedSource = 'mysql-transaction-seed',
      srcAcct.seedBatch = '${BATCH_ID}'
MERGE (src)-[ownSrc:OWNS]->(srcAcct)
  SET ownSrc.seedSource = 'mysql-transaction-seed',
      ownSrc.seedBatch = '${BATCH_ID}'
MERGE (tx:Transaction {transactionId: toInteger(row.id)})
  SET tx.transactionNo = row.transactionNo,
      tx.amount = toFloat(row.amount),
      tx.type = row.transactionType,
      tx.currency = row.currency,
      tx.paymentMethod = row.paymentMethod,
      tx.channel = row.channel,
      tx.crossBorder = row.isCrossBorder = '1',
      tx.sourceSystem = row.sourceSystem,
      tx.time = datetime(replace(row.transactionTime, ' ', 'T')),
      tx.seedSource = 'mysql-transaction-seed',
      tx.seedBatch = '${BATCH_ID}'
MERGE (srcAcct)-[send:SENDS]->(tx)
  SET send.amount = toFloat(row.amount),
      send.transactionNo = row.transactionNo,
      send.seedSource = 'mysql-transaction-seed',
      send.seedBatch = '${BATCH_ID}'
MERGE (dstAcct:Account {accountNo: dstAccountNo})
  SET dstAcct.bank = row.counterpartyBank,
      dstAcct.seedSource = 'mysql-transaction-seed',
      dstAcct.seedBatch = '${BATCH_ID}'
MERGE (tx)-[toRel:TO]->(dstAcct)
  SET toRel.amount = toFloat(row.amount),
      toRel.transactionNo = row.transactionNo,
      toRel.seedSource = 'mysql-transaction-seed',
      toRel.seedBatch = '${BATCH_ID}'
MERGE (dst:Customer {customerId: toInteger(row.counterpartyId)})
  SET dst.name = row.counterpartyName,
      dst.seedSource = 'mysql-transaction-seed',
      dst.seedBatch = '${BATCH_ID}'
MERGE (dst)-[ownDst:OWNS]->(dstAcct)
  SET ownDst.seedSource = 'mysql-transaction-seed',
      ownDst.seedBatch = '${BATCH_ID}'
MERGE (src)-[flow:TRANSFERS_TO {transactionId: toInteger(row.id)}]->(dst)
  SET flow.amount = toFloat(row.amount),
      flow.transactionNo = row.transactionNo,
      flow.time = datetime(replace(row.transactionTime, ' ', 'T')),
      flow.seedSource = 'mysql-transaction-seed',
      flow.seedBatch = '${BATCH_ID}';
CYPHER

echo "=========================================="
echo "  AML Neo4j 交易图谱数据同步"
echo "=========================================="
echo "  DB: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  Neo4j container: ${NEO4J_CONTAINER}"
echo "  Source system: $([ "$ALL_SQL" = 1 ] && echo 'ALL' || echo "$SOURCE_SYSTEM")"
echo "  Customer filter: ${CUSTOMER_ID:-ALL}"
echo "  Batch ID: ${BATCH_ID}"
echo "  Rows: ${ROW_COUNT}"
echo "  TSV: ${TSV_FILE}"
echo "  Cypher: ${CYPHER_FILE}"
echo ""

if [ "$ROW_COUNT" -eq 0 ]; then
    echo "No transactions matched the filter. Seed MySQL business data first or use --all." >&2
    exit 1
fi

echo "Preview:"
head -n 6 "$TSV_FILE" | sed 's/\t/ | /g'
echo ""

if [ "$CYPHER_ONLY" = true ]; then
    echo "--cypher-only enabled. No Neo4j write performed."
    exit 0
fi

run_cypher_in_container() {
    local cypher_file="$1"
    if ! command -v docker >/dev/null 2>&1; then
        echo "docker command not found. Install Docker or run with --cypher-only." >&2
        exit 1
    fi
    if ! docker ps --format '{{.Names}}' | grep -qx "$NEO4J_CONTAINER"; then
        echo "Neo4j container '$NEO4J_CONTAINER' is not running." >&2
        echo "Start it with: docker compose -f docker/docker-compose-dev.yml up -d neo4j" >&2
        exit 1
    fi
    docker exec "$NEO4J_CONTAINER" mkdir -p /var/lib/neo4j/import
    docker cp "$TSV_FILE" "${NEO4J_CONTAINER}:/var/lib/neo4j/import/${IMPORT_NAME}"
    docker exec -i "$NEO4J_CONTAINER" cypher-shell -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" --format plain < "$cypher_file"
}

if [ "$EXECUTE" = true ]; then
    run_cypher_in_container "$CYPHER_FILE"
    echo ""
    echo "Neo4j seed completed."
fi

if [ "$VERIFY" = true ]; then
    VERIFY_FILE="$WORK_DIR/verify-${BATCH_ID}.cypher"
    cat > "$VERIFY_FILE" <<CYPHER
MATCH (n) WHERE n.seedBatch = '${BATCH_ID}'
RETURN labels(n) AS labels, count(n) AS count
ORDER BY labels;

MATCH ()-[r]->() WHERE r.seedBatch = '${BATCH_ID}'
RETURN type(r) AS relationship, count(r) AS count
ORDER BY relationship;

MATCH (c:Customer {seedBatch: '${BATCH_ID}'})-[:OWNS]->(:Account)-[:SENDS]->(t:Transaction)-[:TO]->(:Account)<-[:OWNS]-(cp:Customer)
RETURN c.customerId AS customerId, c.name AS customerName, count(DISTINCT cp) AS counterparties, count(DISTINCT t) AS transactions, sum(t.amount) AS totalAmount
ORDER BY transactions DESC, totalAmount DESC
LIMIT 10;
CYPHER
    run_cypher_in_container "$VERIFY_FILE"
fi
