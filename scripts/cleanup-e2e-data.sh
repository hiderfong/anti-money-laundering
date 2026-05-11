#!/bin/bash
# ============================================================================
# AML System - E2E 测试数据清理脚本
# 默认 dry-run，仅统计将被清理的数据；显式传入 --execute 才会删除。
# ============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-aml_system}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-aml_dev_123}"
E2E_PREFIX="${E2E_PREFIX:-E2E}"
EXECUTE=false
INCLUDE_LEGACY=false

usage() {
    cat <<'EOF'
Usage:
  scripts/cleanup-e2e-data.sh [--execute] [--prefix E2E] [--include-legacy]

Options:
  --execute          实际删除测试数据；不传时只输出 dry-run 统计。
  --prefix VALUE     清理名称/编号以该前缀开头的数据，默认 E2E。
  --include-legacy   同时清理早期脚本产生的固定客户名 E2E测试客户。
  -h, --help         显示帮助。

Environment:
  DB_HOST            默认 127.0.0.1
  DB_PORT            默认 3306
  DB_NAME            默认 aml_system
  DB_USER            默认 root
  DB_PASSWORD        默认 aml_dev_123
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --execute)
            EXECUTE=true
            shift
            ;;
        --prefix)
            E2E_PREFIX="${2:-}"
            shift 2
            ;;
        --include-legacy)
            INCLUDE_LEGACY=true
            shift
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

if [ -z "$E2E_PREFIX" ]; then
    echo "E2E_PREFIX cannot be empty." >&2
    exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
    echo "mysql command not found. Install MySQL client or run cleanup from a machine with mysql CLI." >&2
    exit 1
fi

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

PREFIX_SQL="$(sql_escape "$E2E_PREFIX")"
LEGACY_FILTER="0"
if [ "$INCLUDE_LEGACY" = true ]; then
    LEGACY_FILTER="name = 'E2E测试客户'"
fi

MYSQL_ARGS=(
    -h "$DB_HOST"
    -P "$DB_PORT"
    -u "$DB_USER"
    --database "$DB_NAME"
    --batch
    --raw
)

if [ -n "$DB_PASSWORD" ]; then
    export MYSQL_PWD="$DB_PASSWORD"
fi

read -r -d '' COMMON_SQL <<SQL || true
SET @prefix := '${PREFIX_SQL}';

DROP TEMPORARY TABLE IF EXISTS e2e_customer_ids;
CREATE TEMPORARY TABLE e2e_customer_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_customer_ids
SELECT id
FROM t_customer
WHERE LEFT(name, LENGTH(@prefix)) = @prefix
   OR LEFT(customer_no, LENGTH(@prefix)) = @prefix
   OR LOCATE(LOWER(@prefix), LOWER(COALESCE(email, ''))) > 0
   OR ${LEGACY_FILTER};

DROP TEMPORARY TABLE IF EXISTS e2e_watchlist_source_ids;
CREATE TEMPORARY TABLE e2e_watchlist_source_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_watchlist_source_ids
SELECT id
FROM t_watchlist_source
WHERE LEFT(source_code, LENGTH(@prefix)) = @prefix
   OR LEFT(source_name, LENGTH(@prefix)) = @prefix
   OR created_by = CONCAT(@prefix, '-business-seed');

DROP TEMPORARY TABLE IF EXISTS e2e_watchlist_ids;
CREATE TEMPORARY TABLE e2e_watchlist_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_watchlist_ids
SELECT id
FROM t_watchlist
WHERE source_id IN (SELECT id FROM e2e_watchlist_source_ids)
   OR LEFT(COALESCE(external_id, ''), LENGTH(@prefix)) = @prefix
   OR LEFT(name, LENGTH(@prefix)) = @prefix
   OR LOCATE(@prefix, COALESCE(remarks, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_pep_ids;
CREATE TEMPORARY TABLE e2e_pep_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_pep_ids
SELECT id
FROM t_pep_list
WHERE LEFT(pep_name, LENGTH(@prefix)) = @prefix
   OR data_source = CONCAT(@prefix, '-business-seed');

DROP TEMPORARY TABLE IF EXISTS e2e_transaction_ids;
CREATE TEMPORARY TABLE e2e_transaction_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_transaction_ids
SELECT id
FROM t_transaction
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR LEFT(transaction_no, LENGTH(@prefix)) = @prefix;

DROP TEMPORARY TABLE IF EXISTS e2e_alert_ids;
CREATE TEMPORARY TABLE e2e_alert_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_alert_ids
SELECT id
FROM t_alert
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR LEFT(COALESCE(customer_name, ''), LENGTH(@prefix)) = @prefix
   OR LOCATE(@prefix, COALESCE(alert_summary, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_case_ids;
CREATE TEMPORARY TABLE e2e_case_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_case_ids
SELECT id
FROM t_case
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR alert_id IN (SELECT id FROM e2e_alert_ids)
   OR LOCATE(@prefix, COALESCE(summary, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_str_report_ids;
CREATE TEMPORARY TABLE e2e_str_report_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_str_report_ids
SELECT id
FROM t_str_report
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR case_id IN (SELECT id FROM e2e_case_ids)
   OR LOCATE(@prefix, COALESCE(report_content, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_large_report_ids;
CREATE TEMPORARY TABLE e2e_large_report_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_large_report_ids
SELECT id
FROM t_large_txn_report
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR transaction_id IN (SELECT id FROM e2e_transaction_ids)
   OR LEFT(COALESCE(customer_name, ''), LENGTH(@prefix)) = @prefix;

DROP TEMPORARY TABLE IF EXISTS e2e_screening_request_ids;
CREATE TEMPORARY TABLE e2e_screening_request_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_screening_request_ids
SELECT id
FROM t_screening_request
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR LEFT(request_no, LENGTH(@prefix)) = @prefix
   OR LOCATE(@prefix, COALESCE(request_data, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_product_ids;
CREATE TEMPORARY TABLE e2e_product_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_product_ids
SELECT id
FROM t_product
WHERE LEFT(product_code, LENGTH(@prefix)) = @prefix
   OR LEFT(product_name, LENGTH(@prefix)) = @prefix;

DROP TEMPORARY TABLE IF EXISTS e2e_policy_ids;
CREATE TEMPORARY TABLE e2e_policy_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_policy_ids
SELECT id
FROM t_policy
WHERE customer_id IN (SELECT id FROM e2e_customer_ids)
   OR product_id IN (SELECT id FROM e2e_product_ids)
   OR LEFT(policy_no, LENGTH(@prefix)) = @prefix
   OR LOCATE(@prefix, COALESCE(remark, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_rule_ids;
CREATE TEMPORARY TABLE e2e_rule_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_rule_ids
SELECT id
FROM t_rule_definition
WHERE LEFT(rule_code, LENGTH(@prefix)) = @prefix
   OR LEFT(rule_name, LENGTH(@prefix)) = @prefix
   OR created_by = CONCAT(@prefix, '-business-seed');

DROP TEMPORARY TABLE IF EXISTS e2e_self_assessment_ids;
CREATE TEMPORARY TABLE e2e_self_assessment_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_self_assessment_ids
SELECT id
FROM t_self_assessment
WHERE LOCATE(@prefix, COALESCE(conclusion, '')) > 0;

DROP TEMPORARY TABLE IF EXISTS e2e_indicator_ids;
CREATE TEMPORARY TABLE e2e_indicator_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_indicator_ids
SELECT id
FROM t_assessment_indicator
WHERE LEFT(indicator_code, LENGTH(@prefix)) = @prefix
   OR LEFT(indicator_name, LENGTH(@prefix)) = @prefix;

DROP TEMPORARY TABLE IF EXISTS e2e_user_ids;
CREATE TEMPORARY TABLE e2e_user_ids (id BIGINT PRIMARY KEY) ENGINE=MEMORY;
INSERT IGNORE INTO e2e_user_ids
SELECT id
FROM t_user
WHERE username LIKE 'e2e\\_%' ESCAPE '\\'
   OR LEFT(real_name, LENGTH(@prefix)) = @prefix
   OR LOCATE(LOWER(@prefix), LOWER(COALESCE(email, ''))) > 0;

SELECT 'customers' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_customer_ids;
SELECT 'transactions' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_transaction_ids;
SELECT 'screening_requests' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_screening_request_ids;
SELECT 'watchlist_sources' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_watchlist_source_ids;
SELECT 'watchlists' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_watchlist_ids;
SELECT 'pep_entries' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_pep_ids;
SELECT 'alerts' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_alert_ids;
SELECT 'cases' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_case_ids;
SELECT 'str_reports' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_str_report_ids;
SELECT 'large_txn_reports' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_large_report_ids;
SELECT 'products' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_product_ids;
SELECT 'policies' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_policy_ids;
SELECT 'rules' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_rule_ids;
SELECT 'self_assessments' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_self_assessment_ids;
SELECT 'assessment_indicators' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_indicator_ids;
SELECT 'users' AS artifact, COUNT(*) AS rows_to_clean FROM e2e_user_ids;
SQL

read -r -d '' DELETE_SQL <<'SQL' || true
START TRANSACTION;

DELETE FROM t_notification
WHERE user_id IN (SELECT id FROM e2e_user_ids)
   OR (related_type = 'ALERT' AND related_id IN (SELECT CAST(id AS CHAR) FROM e2e_alert_ids))
   OR (related_type = 'CASE' AND related_id IN (SELECT CAST(id AS CHAR) FROM e2e_case_ids))
   OR (related_type = 'STR' AND related_id IN (SELECT CAST(id AS CHAR) FROM e2e_str_report_ids))
   OR LEFT(title, LENGTH(@prefix)) = @prefix
   OR LOCATE(@prefix, COALESCE(content, '')) > 0;

DELETE FROM t_report_submit_log
WHERE (report_type = 'LARGE_TXN' AND report_id IN (SELECT id FROM e2e_large_report_ids))
   OR (report_type = 'SUSPICIOUS' AND report_id IN (SELECT id FROM e2e_str_report_ids));

DELETE FROM t_str_report WHERE id IN (SELECT id FROM e2e_str_report_ids);
DELETE FROM t_large_txn_report WHERE id IN (SELECT id FROM e2e_large_report_ids);

DELETE FROM t_case_attachment WHERE case_id IN (SELECT id FROM e2e_case_ids);
DELETE FROM t_case_investigation WHERE case_id IN (SELECT id FROM e2e_case_ids);
DELETE FROM t_case_status_log WHERE case_id IN (SELECT id FROM e2e_case_ids);
DELETE FROM t_case WHERE id IN (SELECT id FROM e2e_case_ids);

DELETE FROM t_alert_assignment_log WHERE alert_id IN (SELECT id FROM e2e_alert_ids);
DELETE FROM t_alert_rule_detail WHERE alert_id IN (SELECT id FROM e2e_alert_ids);
DELETE FROM t_alert WHERE id IN (SELECT id FROM e2e_alert_ids);

DELETE FROM t_screening_result
WHERE request_id IN (SELECT id FROM e2e_screening_request_ids)
   OR customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_screening_request WHERE id IN (SELECT id FROM e2e_screening_request_ids);
DELETE FROM t_whitelist WHERE customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_whitelist WHERE watchlist_entry_id IN (SELECT id FROM e2e_watchlist_ids);

DELETE FROM t_watchlist_address WHERE watchlist_id IN (SELECT id FROM e2e_watchlist_ids);
DELETE FROM t_watchlist_identity WHERE watchlist_id IN (SELECT id FROM e2e_watchlist_ids);
DELETE FROM t_watchlist_alias WHERE watchlist_id IN (SELECT id FROM e2e_watchlist_ids);
DELETE FROM t_watchlist WHERE id IN (SELECT id FROM e2e_watchlist_ids);
DELETE FROM t_watchlist_source WHERE id IN (SELECT id FROM e2e_watchlist_source_ids);

DELETE FROM t_pep_relation
WHERE pep_list_id IN (SELECT id FROM e2e_pep_ids)
   OR related_customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_pep_list WHERE id IN (SELECT id FROM e2e_pep_ids);

DELETE FROM t_rule_execution_log
WHERE transaction_id IN (SELECT id FROM e2e_transaction_ids)
   OR customer_id IN (SELECT id FROM e2e_customer_ids)
   OR rule_id IN (SELECT id FROM e2e_rule_ids);
DELETE FROM t_rule_version WHERE rule_id IN (SELECT id FROM e2e_rule_ids);
DELETE FROM t_rule_definition WHERE id IN (SELECT id FROM e2e_rule_ids);
DELETE FROM t_transaction_daily_summary WHERE customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_transaction WHERE id IN (SELECT id FROM e2e_transaction_ids);
DELETE FROM t_policy WHERE id IN (SELECT id FROM e2e_policy_ids);

DELETE FROM t_customer_beneficial_owner WHERE customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_verification_record WHERE customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_customer_risk_rating_log WHERE customer_id IN (SELECT id FROM e2e_customer_ids);
DELETE FROM t_customer WHERE id IN (SELECT id FROM e2e_customer_ids);

DELETE FROM t_product_risk_assessment WHERE product_id IN (SELECT id FROM e2e_product_ids);
DELETE FROM t_product WHERE id IN (SELECT id FROM e2e_product_ids);

DELETE FROM t_rectification_task WHERE assessment_id IN (SELECT id FROM e2e_self_assessment_ids);
DELETE FROM t_assessment_score
WHERE assessment_id IN (SELECT id FROM e2e_self_assessment_ids)
   OR indicator_id IN (SELECT id FROM e2e_indicator_ids);
DELETE FROM t_self_assessment WHERE id IN (SELECT id FROM e2e_self_assessment_ids);
DELETE FROM t_assessment_indicator WHERE id IN (SELECT id FROM e2e_indicator_ids);

DELETE FROM t_user_role WHERE user_id IN (SELECT id FROM e2e_user_ids);
DELETE FROM t_user WHERE id IN (SELECT id FROM e2e_user_ids);

DELETE FROM t_audit_log
WHERE username LIKE 'e2e\\_%' ESCAPE '\\'
   OR LOCATE(@prefix, COALESCE(detail, '')) > 0;

COMMIT;
SQL

echo "=========================================="
echo "  AML E2E 数据清理"
echo "=========================================="
echo "  DB: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  Prefix: ${E2E_PREFIX}"
echo "  Include legacy: ${INCLUDE_LEGACY}"
echo "  Mode: $([ "$EXECUTE" = true ] && echo execute || echo dry-run)"
echo ""

if [ "$EXECUTE" = true ]; then
    printf "%s\n%s\n" "$COMMON_SQL" "$DELETE_SQL" | mysql "${MYSQL_ARGS[@]}"
    echo ""
    echo "Cleanup completed."
else
    printf "%s\n" "$COMMON_SQL" | mysql "${MYSQL_ARGS[@]}"
    echo ""
    echo "Dry-run only. Re-run with --execute to delete these rows."
fi
