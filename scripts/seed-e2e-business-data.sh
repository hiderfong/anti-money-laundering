#!/bin/bash
# ============================================================================
# AML System - E2E 业务闭环测试数据种子脚本
# 默认 dry-run；显式传入 --execute 才会写入数据库。
# ============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-aml_system}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-CHANGE_ME_DEV_DB_PASSWORD}"
E2E_PREFIX="${E2E_PREFIX:-E2E}"
E2E_RUN_ID="${E2E_RUN_ID:-$(date +%Y%m%d%H%M%S)}"
E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-admin123}"
E2E_PASSWORD_HASH="${E2E_PASSWORD_HASH:-\$2a\$10\$c4ISGZ.nKFX0iC34wYd.8.OdmgqOLJXsrmyMocQY67X4j9gjoFojq}"
EXECUTE=false
VERIFY=false
SQL_ONLY=false

usage() {
    cat <<'EOF'
Usage:
  scripts/seed-e2e-business-data.sh [--execute] [--verify] [--sql-only] [--prefix E2E] [--run-id 20260511123000]

Creates a test-only AML business data bundle:
  - three-level organization tree, governance people, shareholders and registrations
  - E2E users for admin/compliance/investigator/viewer style flows
  - customers: normal, sanctions hit, PEP, corporate with beneficial owner
  - watchlist and screening request/result/whitelist data
  - product, policy, transactions, daily summary, rule execution logs
  - alerts, alert assignment/detail, case, investigation, STR report
  - large transaction report, versioned regulatory submissions and receipt history
  - self-assessment, indicators, scores, rectification, audit/notification data

Options:
  --execute          Actually write seed data. Without this flag the script only prints the plan.
                     Combine with --verify to make the intended execute+verify mode explicit.
  --verify           Verify seeded row counts for the selected prefix/run-id.
                     With --execute, verification runs after the seed transaction completes.
  --sql-only         Print generated SQL to stdout without requiring mysql connectivity.
  --prefix VALUE     Data prefix, default E2E. Keep this aligned with cleanup-e2e-data.sh.
  --run-id VALUE     Seed run id, default current timestamp.
  -h, --help         Show help.

Environment:
  DB_HOST            Default 127.0.0.1
  DB_PORT            Default 3306
  DB_NAME            Default aml_system
  DB_USER            Default root
  DB_PASSWORD        Default CHANGE_ME_DEV_DB_PASSWORD
  SEED_DB_CLIENT     auto, mysql, or jdbc. Default auto.
  MYSQL_CONNECTOR_JAR Path to mysql-connector-j jar for JDBC fallback.
  E2E_PASSWORD_HASH  BCrypt hash for E2E users, default is admin123

Cleanup:
  scripts/cleanup-e2e-data.sh --prefix E2E --execute
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
        --sql-only)
            SQL_ONLY=true
            shift
            ;;
        --prefix)
            E2E_PREFIX="${2:-}"
            shift 2
            ;;
        --run-id)
            E2E_RUN_ID="${2:-}"
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

if [ -z "$E2E_PREFIX" ]; then
    echo "E2E_PREFIX cannot be empty." >&2
    exit 1
fi

if [ -z "$E2E_RUN_ID" ]; then
    echo "E2E_RUN_ID cannot be empty." >&2
    exit 1
fi

RUN_KEY_RAW="$(printf "%s" "$E2E_RUN_ID" | tr -cd '[:alnum:]')"
if [ -z "$RUN_KEY_RAW" ]; then
    RUN_KEY_RAW="$(date +%Y%m%d%H%M%S)"
fi
if [ "${#RUN_KEY_RAW}" -gt 14 ]; then
    RUN_KEY="${RUN_KEY_RAW:$((${#RUN_KEY_RAW} - 14))}"
else
    RUN_KEY="$RUN_KEY_RAW"
fi

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

PREFIX_SQL="$(sql_escape "$E2E_PREFIX")"
RUN_ID_SQL="$(sql_escape "$E2E_RUN_ID")"
RUN_KEY_SQL="$(sql_escape "$RUN_KEY")"
PASSWORD_HASH_SQL="$(sql_escape "$E2E_PASSWORD_HASH")"

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

SEED_DB_CLIENT="${SEED_DB_CLIENT:-auto}"
JDBC_RUNNER_DIR=""

cleanup_jdbc_runner() {
    if [ -n "$JDBC_RUNNER_DIR" ] && [ -d "$JDBC_RUNNER_DIR" ]; then
        rm -rf "$JDBC_RUNNER_DIR"
    fi
}
trap cleanup_jdbc_runner EXIT

prepare_jdbc_runner() {
    if [ -n "$JDBC_RUNNER_DIR" ] && [ -f "$JDBC_RUNNER_DIR/E2ESeedSqlRunner.class" ]; then
        return
    fi

    if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
        JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    fi

    local javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
    local java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"

    if ! command -v "$javac_bin" >/dev/null 2>&1 || ! command -v "$java_bin" >/dev/null 2>&1; then
        echo "Java compiler/runtime not found. Install JDK 21 or set JAVA_HOME for JDBC seed execution." >&2
        exit 1
    fi

    MYSQL_CONNECTOR_JAR="${MYSQL_CONNECTOR_JAR:-}"
    if [ -z "$MYSQL_CONNECTOR_JAR" ]; then
        MYSQL_CONNECTOR_JAR="$(find "$HOME/.m2/repository/com/mysql/mysql-connector-j" -name 'mysql-connector-j-*.jar' 2>/dev/null | sort | tail -1 || true)"
    fi

    if [ -z "$MYSQL_CONNECTOR_JAR" ] || [ ! -f "$MYSQL_CONNECTOR_JAR" ]; then
        echo "mysql-connector-j jar not found. Run Maven once or set MYSQL_CONNECTOR_JAR." >&2
        exit 1
    fi

    JDBC_RUNNER_DIR="$(mktemp -d)"
    cat > "$JDBC_RUNNER_DIR/E2ESeedSqlRunner.java" <<'JAVA'
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class E2ESeedSqlRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            throw new IllegalArgumentException("Expected args: host port db user password mode print|quiet");
        }

        String host = args[0];
        String port = args[1];
        String db = args[2];
        String user = args[3];
        String password = args[4];
        boolean printResultSets = "print".equals(args[6]);
        String sql = readStdin();

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&allowMultiQueries=true";

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(sql);
            while (true) {
                if (hasResultSet && printResultSets) {
                    printResultSet(statement.getResultSet());
                }

                int updateCount = statement.getUpdateCount();
                if (!hasResultSet && updateCount == -1) {
                    break;
                }
                hasResultSet = statement.getMoreResults();
            }
        }
    }

    private static String readStdin() throws IOException {
        return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void printResultSet(ResultSet resultSet) throws Exception {
        if (resultSet == null) {
            return;
        }

        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                header.append('\t');
            }
            header.append(metaData.getColumnLabel(i));
        }
        System.out.println(header);

        while (resultSet.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    row.append('\t');
                }
                String value = resultSet.getString(i);
                row.append(value == null ? "NULL" : value);
            }
            System.out.println(row);
        }
    }
}
JAVA

    "$javac_bin" -encoding UTF-8 -cp "$MYSQL_CONNECTOR_JAR" "$JDBC_RUNNER_DIR/E2ESeedSqlRunner.java"
}

run_sql_jdbc() {
    local sql="$1"
    local output_mode="$2"
    prepare_jdbc_runner

    local java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    printf "%s\n" "$sql" | "$java_bin" -Dfile.encoding=UTF-8 -cp "$JDBC_RUNNER_DIR:$MYSQL_CONNECTOR_JAR" E2ESeedSqlRunner \
        "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" "$MODE" "$output_mode"
}

run_sql_mysql() {
    local sql="$1"
    printf "%s\n" "$sql" | mysql "${MYSQL_ARGS[@]}"
}

run_sql() {
    local sql="$1"
    local output_mode="${2:-print}"
    local mysql_error
    mysql_error="$(mktemp)"

    case "$SEED_DB_CLIENT" in
        jdbc)
            run_sql_jdbc "$sql" "$output_mode"
            rm -f "$mysql_error"
            return
            ;;
        mysql)
            run_sql_mysql "$sql"
            rm -f "$mysql_error"
            return
            ;;
        auto)
            ;;
        *)
            echo "Unsupported SEED_DB_CLIENT: $SEED_DB_CLIENT. Use auto, mysql, or jdbc." >&2
            rm -f "$mysql_error"
            exit 1
            ;;
    esac

    if command -v mysql >/dev/null 2>&1; then
        if printf "%s\n" "$sql" | mysql "${MYSQL_ARGS[@]}" 2>"$mysql_error"; then
            rm -f "$mysql_error"
            return
        fi

        cat "$mysql_error" >&2
        if ! grep -Eq "mysql_native_password|Authentication plugin|cannot be loaded" "$mysql_error"; then
            rm -f "$mysql_error"
            exit 1
        fi
        echo "mysql CLI authentication plugin is unavailable. Falling back to JDBC seed runner." >&2
    else
        echo "mysql command not found. Falling back to JDBC seed runner." >&2
    fi

    rm -f "$mysql_error"
    run_sql_jdbc "$sql" "$output_mode"
}

MODE="dry-run"
if [ "$EXECUTE" = true ] && [ "$VERIFY" = true ]; then
    MODE="execute+verify"
elif [ "$EXECUTE" = true ]; then
    MODE="execute"
elif [ "$VERIFY" = true ]; then
    MODE="verify"
elif [ "$SQL_ONLY" = true ]; then
    MODE="sql-only"
fi

read -r -d '' SEED_SQL <<SQL || true
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET collation_connection = 'utf8mb4_unicode_ci';
SET @prefix := '${PREFIX_SQL}';
SET @run_id := '${RUN_ID_SQL}';
SET @run_key := '${RUN_KEY_SQL}';
SET @seed_by := CONCAT(@prefix, '-business-seed');
SET @password_hash := '${PASSWORD_HASH_SQL}';

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1. Organization governance and registration samples
-- ---------------------------------------------------------------------------
SET @org_head_code := CONCAT(@prefix, 'ORGHQ', @run_key);
SET @org_branch_code := CONCAT(@prefix, 'ORGBR', @run_key);
SET @org_outlet_code := CONCAT(@prefix, 'ORGOT', @run_key);

INSERT INTO t_aml_organization
  (id, org_code, org_name, unified_credit_code, lei_code, org_type, parent_id, registered_address,
   business_address, legal_representative, registered_capital, business_scope, regulator_name,
   status, registration_status, created_by, created_time)
SELECT UUID_SHORT(), @org_head_code, '华岳保险股份有限公司', CONCAT('91310000', @run_key, 'HQ'),
       CONCAT('300300', @run_key), 'HEAD_OFFICE', NULL, '上海市浦东新区世纪大道100号',
       '上海市浦东新区世纪大道100号', '周明远', 500000.00,
       '人寿保险、健康保险及经批准的保险资金运用业务', '国家金融监督管理总局上海监管局',
       'ENABLED', 'APPROVED', @seed_by, NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_aml_organization WHERE org_code = @org_head_code);
SELECT @org_head_id := id FROM t_aml_organization WHERE org_code = @org_head_code LIMIT 1;

INSERT INTO t_aml_organization
  (id, org_code, org_name, unified_credit_code, org_type, parent_id, registered_address, business_address,
   legal_representative, business_scope, regulator_name, status, registration_status, created_by, created_time)
SELECT UUID_SHORT(), @org_branch_code, '华岳保险股份有限公司上海分公司', CONCAT('91310115', @run_key, 'BR'),
       'BRANCH', @org_head_id, '上海市浦东新区银城中路88号', '上海市浦东新区银城中路88号',
       '陈景行', '经总公司授权开展保险业务及反洗钱客户管理', '国家金融监督管理总局上海监管局',
       'ENABLED', 'APPROVED', @seed_by, NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_aml_organization WHERE org_code = @org_branch_code);
SELECT @org_branch_id := id FROM t_aml_organization WHERE org_code = @org_branch_code LIMIT 1;

INSERT INTO t_aml_organization
  (id, org_code, org_name, unified_credit_code, org_type, parent_id, registered_address, business_address,
   legal_representative, business_scope, regulator_name, status, registration_status, created_by, created_time)
SELECT UUID_SHORT(), @org_outlet_code, '华岳保险上海分公司浦东陆家嘴营销服务部', CONCAT('91310115', @run_key, 'OT'),
       'OUTLET', @org_branch_id, '上海市浦东新区陆家嘴环路1000号', '上海市浦东新区陆家嘴环路1000号',
       '许安澜', '保险咨询、保全服务和客户身份资料收集', '国家金融监督管理总局上海监管局',
       'DISABLED', 'PENDING_REVIEW', @seed_by, NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_aml_organization WHERE org_code = @org_outlet_code);
SELECT @org_outlet_id := id FROM t_aml_organization WHERE org_code = @org_outlet_code LIMIT 1;

INSERT INTO t_aml_org_person
  (id, organization_id, person_type, person_name, title, department, phone, email, primary_flag, status, created_by, created_time)
SELECT UUID_SHORT(), p.organization_id, p.person_type, p.person_name, p.title, p.department, p.phone, p.email,
       p.primary_flag, 'ENABLED', @seed_by, NOW()
FROM (
  SELECT @org_head_id AS organization_id, 'AML_OFFICER' AS person_type, '顾清和' AS person_name,
         '反洗钱合规负责人' AS title, '法律合规部' AS department, '021-68001234' AS phone,
         CONCAT('aml.officer.', @run_key, '@example.test') AS email, 1 AS primary_flag
  UNION ALL SELECT @org_head_id, 'CONTACT', '沈安宁', '监管联络专员', '法律合规部', '021-68005678', CONCAT('aml.contact.', @run_key, '@example.test'), 1
  UNION ALL SELECT @org_branch_id, 'AML_OFFICER', '唐闻舟', '分公司合规经理', '风险合规部', '021-68881234', CONCAT('branch.aml.', @run_key, '@example.test'), 1
  UNION ALL SELECT @org_branch_id, 'CONTACT', '叶知秋', '分公司监管联络人', '风险合规部', '021-68882345', CONCAT('branch.contact.', @run_key, '@example.test'), 1
  UNION ALL SELECT @org_outlet_id, 'AML_OFFICER', '江云帆', '网点反洗钱专员', '综合管理部', '021-68884567', CONCAT('outlet.aml.', @run_key, '@example.test'), 1
  UNION ALL SELECT @org_outlet_id, 'CONTACT', '林知夏', '网点合规联络人', '综合管理部', '021-68885678', CONCAT('outlet.contact.', @run_key, '@example.test'), 1
) p
WHERE NOT EXISTS (
  SELECT 1 FROM t_aml_org_person x
  WHERE x.organization_id = p.organization_id AND x.person_type = p.person_type AND x.person_name = p.person_name
);

INSERT INTO t_aml_org_shareholder
  (id, organization_id, shareholder_name, shareholder_type, registration_code, ownership_percentage,
   controlling_flag, status, created_by, created_time)
SELECT UUID_SHORT(), @org_head_id, s.shareholder_name, 'ORGANIZATION', s.registration_code,
       s.ownership_percentage, s.controlling_flag, 'ENABLED', @seed_by, NOW()
FROM (
  SELECT '华岳金融控股有限公司' AS shareholder_name, CONCAT('91310000HOLD', @run_key) AS registration_code,
         65.0000 AS ownership_percentage, 1 AS controlling_flag
  UNION ALL SELECT '海川产业投资有限公司', CONCAT('91310000INV', @run_key), 35.0000, 0
) s
WHERE NOT EXISTS (
  SELECT 1 FROM t_aml_org_shareholder x
  WHERE x.organization_id = @org_head_id AND x.shareholder_name = s.shareholder_name
);

INSERT INTO t_aml_org_registration
  (id, registration_no, organization_id, registration_type, version, status, commitment_accepted,
   snapshot_json, submitted_by, submitted_at, reviewed_by, reviewed_at, review_opinion, created_by, created_time)
SELECT UUID_SHORT(), r.registration_no, r.organization_id, 'INITIAL', 1, r.status, 1,
       CONCAT('{"seedRunId":"', @run_id, '","organizationCode":"', r.org_code, '"}'),
       'e2e_compliance', DATE_SUB(NOW(), INTERVAL 1 DAY), r.reviewed_by, r.reviewed_at,
       r.review_opinion, @seed_by, NOW()
FROM (
  SELECT CONCAT(@prefix, 'ORGREGHQ', @run_key) AS registration_no, @org_head_id AS organization_id,
         @org_head_code AS org_code, 'APPROVED' AS status, 'e2e_seed_operator' AS reviewed_by,
         NOW() AS reviewed_at, '机构登记资料完整，同意登记。' AS review_opinion
  UNION ALL SELECT CONCAT(@prefix, 'ORGREGBR', @run_key), @org_branch_id, @org_branch_code,
         'APPROVED', 'e2e_seed_operator', NOW(), '分支机构登记资料完整，同意登记。'
  UNION ALL SELECT CONCAT(@prefix, 'ORGREGOT', @run_key), @org_outlet_id, @org_outlet_code,
         'PENDING_REVIEW', NULL, NULL, NULL
) r
WHERE NOT EXISTS (SELECT 1 FROM t_aml_org_registration x WHERE x.registration_no = r.registration_no);

INSERT INTO t_aml_org_review_log
  (id, registration_id, action_type, from_status, to_status, opinion, operator, operated_at, created_by, created_time)
SELECT UUID_SHORT(), r.id, l.action_type, l.from_status, l.to_status, l.opinion, l.operator, l.operated_at, @seed_by, NOW()
FROM t_aml_org_registration r
CROSS JOIN (
  SELECT 'SUBMIT' AS action_type, 'DRAFT' AS from_status, 'PENDING_REVIEW' AS to_status,
         '提交机构登记审核' AS opinion, 'e2e_compliance' AS operator, DATE_SUB(NOW(), INTERVAL 1 DAY) AS operated_at
) l
WHERE r.registration_no IN (CONCAT(@prefix, 'ORGREGHQ', @run_key), CONCAT(@prefix, 'ORGREGBR', @run_key), CONCAT(@prefix, 'ORGREGOT', @run_key))
  AND NOT EXISTS (SELECT 1 FROM t_aml_org_review_log x WHERE x.registration_id = r.id AND x.action_type = 'SUBMIT');

INSERT INTO t_aml_org_review_log
  (id, registration_id, action_type, from_status, to_status, opinion, operator, operated_at, created_by, created_time)
SELECT UUID_SHORT(), r.id, 'APPROVE', 'PENDING_REVIEW', 'APPROVED', r.review_opinion,
       r.reviewed_by, r.reviewed_at, @seed_by, NOW()
FROM t_aml_org_registration r
WHERE r.registration_no IN (CONCAT(@prefix, 'ORGREGHQ', @run_key), CONCAT(@prefix, 'ORGREGBR', @run_key))
  AND NOT EXISTS (SELECT 1 FROM t_aml_org_review_log x WHERE x.registration_id = r.id AND x.action_type = 'APPROVE');

-- ---------------------------------------------------------------------------
-- 2. External integration connectors, jobs and runs
-- ---------------------------------------------------------------------------
SET @connector_core_code := CONCAT(@prefix, 'INTCORE', @run_key);
SET @connector_watchlist_code := CONCAT(@prefix, 'INTWL', @run_key);
SET @connector_regulator_code := CONCAT(@prefix, 'INTREG', @run_key);
SET @connector_identity_code := CONCAT(@prefix, 'INTID', @run_key);

INSERT INTO t_integration_connector
  (id, connector_code, connector_name, business_type, transport_type, endpoint_url, auth_type,
   credential_ref, status, health_status, timeout_seconds, max_retries, retry_interval_seconds,
   last_health_check_time, last_success_time, last_failure_time, last_error_message, description,
   created_by, created_time)
SELECT UUID_SHORT(), c.connector_code, c.connector_name, c.business_type, 'MOCK', c.endpoint_url, 'NONE',
       NULL, 'ENABLED', c.health_status, 30, c.max_retries, 10, c.last_health_check_time,
       c.last_success_time, c.last_failure_time, c.last_error_message,
       CONCAT(@prefix, '集成中心业务闭环样本 ', @run_id), @seed_by, NOW()
FROM (
  SELECT @connector_core_code AS connector_code, '华岳核心业务交易数据交换平台' AS connector_name,
         'CORE_BUSINESS' AS business_type, 'mock://success?read=500&written=498&skipped=2' AS endpoint_url,
         'HEALTHY' AS health_status, 2 AS max_retries, NOW() AS last_health_check_time,
         NOW() AS last_success_time, NULL AS last_failure_time, NULL AS last_error_message
  UNION ALL SELECT @connector_watchlist_code, '国际制裁名单数据交换平台', 'WATCHLIST',
         'mock://flaky?failures=1&read=120&written=120', 'HEALTHY', 2, NOW(), NOW(), NULL, NULL
  UNION ALL SELECT @connector_regulator_code, '监管报送前置交换平台', 'REGULATORY_REPORTING',
         'mock://success?receipt=ACCEPTED', 'HEALTHY', 1, NOW(), NOW(), DATE_SUB(NOW(), INTERVAL 40 MINUTE), NULL
  UNION ALL SELECT @connector_identity_code, '客户身份联网核验平台', 'IDENTITY_VERIFICATION',
         'mock://success?read=80&written=80', 'UNKNOWN', 2, NULL, NULL, NULL, NULL
) c
WHERE NOT EXISTS (SELECT 1 FROM t_integration_connector x WHERE x.connector_code = c.connector_code);

SELECT @connector_core_id := id FROM t_integration_connector WHERE connector_code = @connector_core_code LIMIT 1;
SELECT @connector_watchlist_id := id FROM t_integration_connector WHERE connector_code = @connector_watchlist_code LIMIT 1;
SELECT @connector_regulator_id := id FROM t_integration_connector WHERE connector_code = @connector_regulator_code LIMIT 1;
SELECT @connector_identity_id := id FROM t_integration_connector WHERE connector_code = @connector_identity_code LIMIT 1;

SET @integration_job_core_code := CONCAT(@prefix, 'JOBTX', @run_key);
SET @integration_job_watchlist_code := CONCAT(@prefix, 'JOBWL', @run_key);
SET @integration_job_regulator_code := CONCAT(@prefix, 'JOBREG', @run_key);
SET @integration_job_identity_code := CONCAT(@prefix, 'JOBID', @run_key);

INSERT INTO t_integration_job
  (id, job_code, job_name, connector_id, business_object, direction, cron_expression, batch_size,
   max_retries, enabled, execution_status, last_run_time, next_run_time, description, created_by, created_time)
SELECT UUID_SHORT(), j.job_code, j.job_name, j.connector_id, j.business_object, j.direction,
       j.cron_expression, j.batch_size, j.max_retries, 1, j.execution_status, j.last_run_time,
       j.next_run_time, CONCAT(@prefix, '集成任务闭环样本 ', @run_id), @seed_by, NOW()
FROM (
  SELECT @integration_job_core_code AS job_code, '核心业务交易增量同步' AS job_name,
         @connector_core_id AS connector_id, 'TRANSACTION' AS business_object, 'INBOUND' AS direction,
         '0 0/30 * * * ?' AS cron_expression, 1000 AS batch_size, 2 AS max_retries,
         'SUCCESS' AS execution_status, DATE_SUB(NOW(), INTERVAL 20 MINUTE) AS last_run_time,
         DATE_ADD(NOW(), INTERVAL 10 MINUTE) AS next_run_time
  UNION ALL SELECT @integration_job_watchlist_code, '国际制裁名单增量更新', @connector_watchlist_id,
         'WATCHLIST', 'INBOUND', '0 0 2 * * ?', 5000, 2, 'SUCCESS', DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 23 HOUR)
  UNION ALL SELECT @integration_job_regulator_code, '可疑交易报告批量报送', @connector_regulator_id,
         'REGULATORY_REPORT', 'OUTBOUND', '0 30 1 * * ?', 200, 1, 'FAILED', DATE_SUB(NOW(), INTERVAL 40 MINUTE), DATE_ADD(NOW(), INTERVAL 23 HOUR)
  UNION ALL SELECT @integration_job_identity_code, '客户身份核验结果回收', @connector_identity_id,
         'IDENTITY_RESULT', 'BIDIRECTIONAL', '0 0/15 * * * ?', 500, 2, 'IDLE', NULL, DATE_ADD(NOW(), INTERVAL 15 MINUTE)
) j
WHERE NOT EXISTS (SELECT 1 FROM t_integration_job x WHERE x.job_code = j.job_code);

SELECT @integration_job_core_id := id FROM t_integration_job WHERE job_code = @integration_job_core_code LIMIT 1;
SELECT @integration_job_watchlist_id := id FROM t_integration_job WHERE job_code = @integration_job_watchlist_code LIMIT 1;
SELECT @integration_job_regulator_id := id FROM t_integration_job WHERE job_code = @integration_job_regulator_code LIMIT 1;

INSERT INTO t_integration_run
  (id, run_no, job_id, connector_id, retry_of_run_id, trigger_type, status, attempt_count, retry_count,
   records_read, records_written, records_skipped, error_count, started_time, completed_time, duration_ms,
   request_summary, response_summary, error_message, trace_id, executed_by, created_by, created_time)
SELECT UUID_SHORT(), r.run_no, r.job_id, r.connector_id, NULL, r.trigger_type, r.status, r.attempt_count,
       r.retry_count, r.records_read, r.records_written, r.records_skipped, r.error_count,
       r.started_time, r.completed_time, r.duration_ms, CONCAT('E2E_RUN_ID=', @run_id),
       r.response_summary, r.error_message, CONCAT(@prefix, 'INTTRACE', @run_key, r.trace_suffix),
       r.executed_by, @seed_by, NOW()
FROM (
  SELECT CONCAT(@prefix, 'INTRUN', @run_key, '01') AS run_no, @integration_job_core_id AS job_id,
         @connector_core_id AS connector_id, 'SCHEDULED' AS trigger_type, 'SUCCESS' AS status,
         1 AS attempt_count, 0 AS retry_count, 500 AS records_read, 498 AS records_written,
         2 AS records_skipped, 0 AS error_count, DATE_SUB(NOW(), INTERVAL 20 MINUTE) AS started_time,
         DATE_ADD(DATE_SUB(NOW(), INTERVAL 20 MINUTE), INTERVAL 860000 MICROSECOND) AS completed_time,
         860 AS duration_ms, '核心交易同步完成' AS response_summary, NULL AS error_message,
         '01' AS trace_suffix, 'scheduler' AS executed_by
  UNION ALL SELECT CONCAT(@prefix, 'INTRUN', @run_key, '02'), @integration_job_watchlist_id,
         @connector_watchlist_id, 'SCHEDULED', 'SUCCESS', 2, 1, 120, 120, 0, 0,
         DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 HOUR), INTERVAL 1240000 MICROSECOND),
         1240, '名单同步在一次重试后完成', NULL, '02', 'scheduler'
  UNION ALL SELECT CONCAT(@prefix, 'INTRUN', @run_key, '03'), @integration_job_regulator_id,
         @connector_regulator_id, 'MANUAL', 'FAILED', 2, 1, 20, 0, 0, 20,
         DATE_SUB(NOW(), INTERVAL 40 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 40 MINUTE), INTERVAL 720000 MICROSECOND),
         720, NULL, '模拟监管前置平台维护中', '03', 'e2e_compliance'
) r
WHERE NOT EXISTS (SELECT 1 FROM t_integration_run x WHERE x.run_no = r.run_no);

-- ---------------------------------------------------------------------------
-- 3. E2E users and role bindings
-- ---------------------------------------------------------------------------
INSERT INTO t_user
  (username, password_hash, real_name, email, phone, department, position, organization_id, status, remark, created_by, created_time)
VALUES
  ('e2e_seed_operator', @password_hash, CONCAT(@prefix, '种子管理员'), CONCAT('e2e_seed_operator_', @run_key, '@test.local'), '13900002001', 'E2E测试部', '种子数据管理员', @org_head_id, 'ENABLED', CONCAT(@prefix, '业务闭环种子用户 ', @run_id), @seed_by, NOW()),
  ('e2e_compliance', @password_hash, CONCAT(@prefix, '合规专员'), CONCAT('e2e_compliance_', @run_key, '@test.local'), '13900002002', 'E2E测试部', '合规专员', @org_head_id, 'ENABLED', CONCAT(@prefix, '业务闭环种子用户 ', @run_id), @seed_by, NOW()),
  ('e2e_investigator', @password_hash, CONCAT(@prefix, '调查员'), CONCAT('e2e_investigator_', @run_key, '@test.local'), '13900002003', 'E2E测试部', '调查员', @org_branch_id, 'ENABLED', CONCAT(@prefix, '业务闭环种子用户 ', @run_id), @seed_by, NOW()),
  ('e2e_viewer', @password_hash, CONCAT(@prefix, '只读用户'), CONCAT('e2e_viewer_', @run_key, '@test.local'), '13900002004', 'E2E测试部', '只读用户', @org_outlet_id, 'ENABLED', CONCAT(@prefix, '业务闭环种子用户 ', @run_id), @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  real_name = VALUES(real_name),
  email = VALUES(email),
  phone = VALUES(phone),
  department = VALUES(department),
  position = VALUES(position),
  organization_id = VALUES(organization_id),
  status = 'ENABLED',
  remark = VALUES(remark),
  updated_by = @seed_by,
  updated_time = NOW();

INSERT IGNORE INTO t_user_role (user_id, role_id, created_by, created_time)
SELECT u.id, r.id, @seed_by, NOW()
FROM t_user u
JOIN t_role r ON (
    (u.username = 'e2e_seed_operator' AND r.role_code = 'ROLE_ADMIN')
 OR (u.username = 'e2e_compliance' AND r.role_code = 'ROLE_COMPLIANCE')
 OR (u.username = 'e2e_investigator' AND r.role_code = 'ROLE_INVESTIGATOR')
 OR (u.username = 'e2e_viewer' AND r.role_code = 'ROLE_VIEWER')
)
WHERE u.username IN ('e2e_seed_operator', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer');

SELECT @operator_id := id FROM t_user WHERE username = 'e2e_seed_operator' LIMIT 1;
SELECT @compliance_id := COALESCE((SELECT id FROM t_user WHERE username = 'e2e_compliance' LIMIT 1), @operator_id);
SELECT @investigator_id := COALESCE((SELECT id FROM t_user WHERE username = 'e2e_investigator' LIMIT 1), @operator_id);

-- ---------------------------------------------------------------------------
-- 4. Customers, KYC records, PEP and beneficial owner data
-- ---------------------------------------------------------------------------
SET @normal_customer_no := CONCAT(@prefix, 'C', @run_key, '01');
SET @sanction_customer_no := CONCAT(@prefix, 'C', @run_key, '02');
SET @pep_customer_no := CONCAT(@prefix, 'C', @run_key, '03');
SET @corp_customer_no := CONCAT(@prefix, 'C', @run_key, '04');
SET @chain_customer_01_no := CONCAT(@prefix, 'C', @run_key, '05');
SET @chain_customer_02_no := CONCAT(@prefix, 'C', @run_key, '06');
SET @chain_customer_03_no := CONCAT(@prefix, 'C', @run_key, '07');
SET @normal_customer_name := '张晨曦';
SET @sanction_customer_name := 'Grace Miller';
SET @pep_customer_name := '周建国';
SET @corp_customer_name := '上海华颐供应链管理有限公司';
SET @chain_customer_01_name := '深圳前海星汇贸易有限公司';
SET @chain_customer_02_name := '厦门融达进出口有限公司';
SET @chain_customer_03_name := '宁波海泽物流有限公司';
SET @beneficial_owner_name := '陈启明';

INSERT INTO t_customer
  (customer_no, customer_type, name, name_en, gender, nationality, birth_date, id_type, id_number, address, residence_address, phone, email, occupation, employer, job_title, annual_income_range, tax_resident_status, risk_level, risk_score, risk_update_time, is_pep, pep_type, is_sanctioned, kyc_status, kyc_last_review_time, kyc_next_review_time, remark, status, created_by, created_time)
VALUES
  (@normal_customer_no, 'INDIVIDUAL', @normal_customer_name, 'Chenxi Zhang', 'MALE', 'CN', '1990-01-01', 'IDCARD', CONCAT('11010119900101', RIGHT(@run_key, 4)), '北京市朝阳区建国路88号华贸中心A座', '北京市朝阳区建国路88号华贸中心A座', CONCAT('139', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('zhang.chenxi.', @run_key, '@example.com'), '软件研发工程师', '北京云澜科技有限公司', '高级工程师', '300K_500K', 'CHINA', 'LOW', 18, NOW(), 0, NULL, 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 3 YEAR), CONCAT(@prefix, '闭环普通客户 ', @run_id), 'ACTIVE', @seed_by, NOW()),
  (@sanction_customer_no, 'INDIVIDUAL', @sanction_customer_name, 'Grace Miller', 'FEMALE', 'US', '1982-02-02', 'PASSPORT', CONCAT('US', @run_key), '上海市浦东新区世纪大道100号环球金融中心', '上海市浦东新区世纪大道100号环球金融中心', CONCAT('138', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('grace.miller.', @run_key, '@example.com'), '贵金属贸易顾问', '上海鼎源珠宝贸易有限公司', '实际控制人', '1000K_PLUS', 'OTHER', 'HIGH', 92, NOW(), 0, NULL, 1, 'REVIEWING', NOW(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), CONCAT(@prefix, '闭环制裁命中客户 ', @run_id), 'ACTIVE', @seed_by, NOW()),
  (@pep_customer_no, 'INDIVIDUAL', @pep_customer_name, 'Jianguo Zhou', 'MALE', 'CN', '1975-03-03', 'IDCARD', CONCAT('11010119750303', RIGHT(@run_key, 4)), '广州市天河区珠江新城华夏路16号', '广州市天河区珠江新城华夏路16号', CONCAT('137', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('zhou.jianguo.', @run_key, '@example.com'), '国有企业高级管理人员', '广州市城市建设投资集团有限公司', '副总经理', '500K_1000K', 'CHINA', 'HIGH', 86, NOW(), 1, 'DOMESTIC', 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), CONCAT(@prefix, '闭环PEP客户 ', @run_id), 'ACTIVE', @seed_by, NOW()),
  (@corp_customer_no, 'CORPORATE', @corp_customer_name, 'Shanghai Huayi Supply Chain Management Co., Ltd.', NULL, 'CN', '2018-04-04', 'OTHER', CONCAT('91310000MA', @run_key), '深圳市南山区粤海街道科苑南路2666号', '深圳市南山区粤海街道科苑南路2666号', CONCAT('136', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('compliance.', @run_key, '@huayi-supply.example'), NULL, NULL, NULL, '1000K_PLUS', 'CHINA', 'MEDIUM', 61, NOW(), 0, NULL, 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 2 YEAR), CONCAT(@prefix, '闭环法人客户 ', @run_id), 'ACTIVE', @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  name_en = VALUES(name_en),
  address = VALUES(address),
  residence_address = VALUES(residence_address),
  occupation = VALUES(occupation),
  employer = VALUES(employer),
  job_title = VALUES(job_title),
  email = VALUES(email),
  risk_level = VALUES(risk_level),
  risk_score = VALUES(risk_score),
  is_pep = VALUES(is_pep),
  pep_type = VALUES(pep_type),
  is_sanctioned = VALUES(is_sanctioned),
  kyc_status = VALUES(kyc_status),
  remark = VALUES(remark),
  updated_by = @seed_by,
  updated_time = NOW();

SELECT @normal_customer_id := id FROM t_customer WHERE customer_no = @normal_customer_no LIMIT 1;
SELECT @sanction_customer_id := id FROM t_customer WHERE customer_no = @sanction_customer_no LIMIT 1;
SELECT @pep_customer_id := id FROM t_customer WHERE customer_no = @pep_customer_no LIMIT 1;
SELECT @corp_customer_id := id FROM t_customer WHERE customer_no = @corp_customer_no LIMIT 1;

INSERT INTO t_customer
  (customer_no, customer_type, name, name_en, nationality, birth_date, id_type, id_number, address, residence_address, phone, email, annual_income_range, tax_resident_status, risk_level, risk_score, risk_update_time, is_pep, is_sanctioned, kyc_status, kyc_last_review_time, kyc_next_review_time, remark, status, created_by, created_time)
VALUES
  (@chain_customer_01_no, 'CORPORATE', @chain_customer_01_name, 'Shenzhen Qianhai Xinghui Trading Co., Ltd.', 'CN', '2020-05-12', 'OTHER', CONCAT('91440300MA', @run_key, 'A'), '深圳市前海深港合作区前湾一路1号A栋201室', '深圳市前海深港合作区前湾一路1号A栋201室', CONCAT('135', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('finance.', @run_key, '@xinghui-trade.example'), '1000K_PLUS', 'CHINA', 'MEDIUM', 68, NOW(), 0, 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), CONCAT(@prefix, '复杂交易链路一跳中转客户 ', @run_id), 'ACTIVE', @seed_by, NOW()),
  (@chain_customer_02_no, 'CORPORATE', @chain_customer_02_name, 'Xiamen Rongda Import Export Co., Ltd.', 'CN', '2019-09-18', 'OTHER', CONCAT('91350200MA', @run_key, 'B'), '厦门市湖里区枋湖北二路889号', '厦门市湖里区枋湖北二路889号', CONCAT('134', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('settlement.', @run_key, '@rongda-ie.example'), '1000K_PLUS', 'CHINA', 'HIGH', 78, NOW(), 0, 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), CONCAT(@prefix, '复杂交易链路二跳中转客户 ', @run_id), 'ACTIVE', @seed_by, NOW()),
  (@chain_customer_03_no, 'CORPORATE', @chain_customer_03_name, 'Ningbo Haize Logistics Co., Ltd.', 'CN', '2021-11-03', 'OTHER', CONCAT('91330200MA', @run_key, 'C'), '宁波市北仑区新碶街道明州路500号', '宁波市北仑区新碶街道明州路500号', CONCAT('133', LPAD(RIGHT(@run_key, 8), 8, '0')), CONCAT('ops.', @run_key, '@haize-logistics.example'), '1000K_PLUS', 'CHINA', 'HIGH', 82, NOW(), 0, 0, 'COMPLETE', NOW(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), CONCAT(@prefix, '复杂交易链路三跳中转客户 ', @run_id), 'ACTIVE', @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  name_en = VALUES(name_en),
  address = VALUES(address),
  residence_address = VALUES(residence_address),
  email = VALUES(email),
  risk_level = VALUES(risk_level),
  risk_score = VALUES(risk_score),
  kyc_status = VALUES(kyc_status),
  remark = VALUES(remark),
  updated_by = @seed_by,
  updated_time = NOW();

SELECT @chain_customer_01_id := id FROM t_customer WHERE customer_no = @chain_customer_01_no LIMIT 1;
SELECT @chain_customer_02_id := id FROM t_customer WHERE customer_no = @chain_customer_02_no LIMIT 1;
SELECT @chain_customer_03_id := id FROM t_customer WHERE customer_no = @chain_customer_03_no LIMIT 1;

INSERT INTO t_customer_beneficial_owner
  (customer_id, owner_name, owner_id_type, owner_id_number, nationality, birth_date, ownership_percentage, control_type, control_description, relationship, status, created_by, created_time)
SELECT @corp_customer_id, @beneficial_owner_name, 'IDCARD', CONCAT('11010119770101', RIGHT(@run_key, 4)), 'CN', '1977-01-01', 75.00, 'EQUITY', CONCAT(@prefix, '法人客户控股股东 ', @run_id), '控股股东', 'ACTIVE', @seed_by, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_customer_beneficial_owner WHERE customer_id = @corp_customer_id AND LOCATE(@run_id, COALESCE(control_description, '')) > 0
);
UPDATE t_customer_beneficial_owner
SET owner_name = @beneficial_owner_name,
    control_description = CONCAT(@prefix, '法人客户控股股东 ', @run_id)
WHERE customer_id = @corp_customer_id
  AND LOCATE(@run_id, COALESCE(control_description, '')) > 0;

INSERT INTO t_verification_record
  (customer_id, verification_type, verification_result, request_data, response_data, third_party_provider, verified_by, verified_time, created_time)
SELECT customer_id, verification_type, 'SUCCESS', request_data, '{"status":"PASS","seed":true}', 'E2E-MOCK', @operator_id, NOW(), NOW()
FROM (
  SELECT @normal_customer_id AS customer_id, 'FOUR_FACTOR' AS verification_type, CONCAT('{"runId":"', @run_id, '","scenario":"normal"}') AS request_data
  UNION ALL SELECT @sanction_customer_id, 'PASSPORT', CONCAT('{"runId":"', @run_id, '","scenario":"sanction"}')
  UNION ALL SELECT @corp_customer_id, 'ENTERPRISE', CONCAT('{"runId":"', @run_id, '","scenario":"corporate"}')
) v
WHERE NOT EXISTS (
  SELECT 1 FROM t_verification_record r
  WHERE r.customer_id = v.customer_id AND r.verification_type = v.verification_type AND LOCATE(@run_id, COALESCE(r.request_data, '')) > 0
);

INSERT INTO t_customer_risk_rating_log
  (customer_id, old_risk_level, new_risk_level, old_risk_score, new_risk_score, change_reason, change_type, changed_by, changed_time)
SELECT customer_id, old_risk_level, new_risk_level, old_risk_score, new_risk_score, CONCAT(@prefix, '业务闭环风险评级 ', @run_id), 'AUTO', @seed_by, NOW()
FROM (
  SELECT @sanction_customer_id AS customer_id, 'MEDIUM' AS old_risk_level, 'HIGH' AS new_risk_level, 58 AS old_risk_score, 92 AS new_risk_score
  UNION ALL SELECT @pep_customer_id, 'MEDIUM', 'HIGH', 54, 86
  UNION ALL SELECT @corp_customer_id, 'LOW', 'MEDIUM', 24, 61
) r
WHERE NOT EXISTS (
  SELECT 1 FROM t_customer_risk_rating_log l
  WHERE l.customer_id = r.customer_id AND LOCATE(@run_id, COALESCE(l.change_reason, '')) > 0
);

INSERT INTO t_pep_list
  (pep_name, pep_name_en, pep_type, pep_position, pep_country, pep_organization, data_source, effective_date, status, created_time)
SELECT @pep_customer_name, 'Jianguo Zhou', 'DOMESTIC', '国有企业副总经理', 'CN', '广州市城市建设投资集团有限公司', @seed_by, CURDATE(), 'ACTIVE', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_pep_list WHERE data_source = @seed_by AND pep_name = @pep_customer_name);
SELECT @pep_list_id := id FROM t_pep_list WHERE data_source = @seed_by AND pep_name = @pep_customer_name LIMIT 1;

INSERT INTO t_pep_relation
  (pep_list_id, related_customer_id, relation_type, relation_description, status, created_time)
SELECT @pep_list_id, @pep_customer_id, 'ASSOCIATE', CONCAT(@prefix, 'PEP测试关联 ', @run_id), 'ACTIVE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_pep_relation WHERE pep_list_id = @pep_list_id AND related_customer_id = @pep_customer_id
);

-- ---------------------------------------------------------------------------
-- 3. Watchlist, screening and whitelist data
-- ---------------------------------------------------------------------------
SET @source_code := CONCAT(@prefix, 'SRC', @run_key);
SET @watchlist_external_id := CONCAT(@prefix, 'WL', @run_key, '01');

INSERT INTO t_watchlist_source
  (source_code, source_name, source_type, update_frequency, file_format, file_url, last_update_time, next_update_time, total_entries, status, created_by, created_time)
VALUES
  (@source_code, '国际制裁名单日终镜像', 'OTHER', 'DAILY', 'JSON', CONCAT('mock://', @prefix, '/', @run_id, '/watchlist.json'), NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 1, 'ENABLED', @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  source_name = VALUES(source_name),
  file_url = VALUES(file_url),
  last_update_time = VALUES(last_update_time),
  next_update_time = VALUES(next_update_time),
  total_entries = VALUES(total_entries),
  status = 'ENABLED',
  updated_by = @seed_by,
  updated_time = NOW();
SELECT @watchlist_source_id := id FROM t_watchlist_source WHERE source_code = @source_code LIMIT 1;

INSERT INTO t_watchlist
  (source_id, external_id, entity_type, name, name_en, gender, nationality, date_of_birth, place_of_birth, remarks, list_date, effective_date, status, created_time)
SELECT @watchlist_source_id, @watchlist_external_id, 'INDIVIDUAL', @sanction_customer_name, 'Grace Miller', 'FEMALE', 'US', '1982-02-02', 'New York', CONCAT(@prefix, '业务闭环名单命中样本 ', @run_id), CURDATE(), CURDATE(), 'ACTIVE', NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_watchlist WHERE source_id = @watchlist_source_id AND external_id = @watchlist_external_id
);
SELECT @watchlist_id := id FROM t_watchlist WHERE source_id = @watchlist_source_id AND external_id = @watchlist_external_id LIMIT 1;
UPDATE t_watchlist
SET name = @sanction_customer_name,
    name_en = 'Grace Miller',
    place_of_birth = 'New York'
WHERE id = @watchlist_id;

INSERT INTO t_watchlist_alias (watchlist_id, alias_name, alias_type, language, created_time)
SELECT @watchlist_id, 'G. Miller', 'AKA', 'en', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_watchlist_alias WHERE watchlist_id = @watchlist_id AND alias_name = 'G. Miller');

INSERT INTO t_watchlist_identity (watchlist_id, id_type, id_number, issuing_country, expiry_date, created_time)
SELECT @watchlist_id, 'PASSPORT', CONCAT('E2EPASS', @run_key), 'US', DATE_ADD(CURDATE(), INTERVAL 5 YEAR), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_watchlist_identity WHERE watchlist_id = @watchlist_id AND id_number = CONCAT('E2EPASS', @run_key));

SET @screening_request_no := CONCAT(@prefix, 'SCR', @run_key, '01');
INSERT INTO t_screening_request
  (request_no, customer_id, screening_type, request_source, request_data, total_scanned, total_hit, status, created_time, completed_time)
VALUES
  (@screening_request_no, @sanction_customer_id, 'CUSTOMER_ONBOARD', 'KYC', CONCAT('{"runId":"', @run_id, '","scenario":"sanctions-hit"}'), 1, 1, 'COMPLETED', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  customer_id = VALUES(customer_id),
  request_data = VALUES(request_data),
  total_scanned = VALUES(total_scanned),
  total_hit = VALUES(total_hit),
  status = VALUES(status),
  completed_time = NOW();
SELECT @screening_request_id := id FROM t_screening_request WHERE request_no = @screening_request_no LIMIT 1;

INSERT INTO t_screening_result
  (request_id, customer_id, customer_name, customer_id_number, watchlist_entry_id, watchlist_name, match_score, match_type, match_field, match_detail, review_status, review_result, review_reason, reviewed_by, reviewed_time, whitelisted, created_time)
SELECT @screening_request_id, @sanction_customer_id, @sanction_customer_name, CONCAT('US', @run_key), @watchlist_id, @sanction_customer_name, 98.50, 'EXACT', 'name,id_number', CONCAT('{"runId":"', @run_id, '","matched":["name","id_number"]}'), 'CONFIRMED', 'TRUE_HIT', CONCAT(@prefix, '人工复核确认为真实命中'), 'e2e_compliance', NOW(), 0, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_screening_result
  WHERE request_id = @screening_request_id AND customer_id = @sanction_customer_id AND watchlist_entry_id = @watchlist_id
);
UPDATE t_screening_result
SET customer_name = @sanction_customer_name,
    customer_id_number = CONCAT('US', @run_key),
    watchlist_name = @sanction_customer_name,
    review_reason = CONCAT(@prefix, '人工复核确认为真实命中')
WHERE request_id = @screening_request_id
  AND customer_id = @sanction_customer_id
  AND watchlist_entry_id = @watchlist_id;

INSERT INTO t_whitelist
  (customer_id, customer_name, watchlist_entry_id, watchlist_name, exclude_reason, evidence, effective_date, expiry_date, approved_by, approved_time, review_status, created_by, created_time)
SELECT @normal_customer_id, @normal_customer_name, @watchlist_id, @sanction_customer_name, CONCAT(@prefix, '证件号、出生日期和工作单位均不一致，确认同名不同人'), CONCAT('{"runId":"', @run_id, '","evidence":"manual-review"}'), CURDATE(), DATE_ADD(CURDATE(), INTERVAL 180 DAY), 'e2e_compliance', NOW(), 'ACTIVE', @seed_by, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_whitelist WHERE customer_id = @normal_customer_id AND watchlist_entry_id = @watchlist_id AND LOCATE(@prefix, exclude_reason) > 0
);
UPDATE t_whitelist
SET customer_name = @normal_customer_name,
    watchlist_name = @sanction_customer_name,
    exclude_reason = CONCAT(@prefix, '证件号、出生日期和工作单位均不一致，确认同名不同人')
WHERE customer_id = @normal_customer_id
  AND watchlist_entry_id = @watchlist_id
  AND LOCATE(@prefix, exclude_reason) > 0;

-- ---------------------------------------------------------------------------
-- 4. Product, policy, transactions, rules and daily summary
-- ---------------------------------------------------------------------------
SET @product_code := CONCAT(@prefix, 'P', @run_key);
SET @policy_no := CONCAT(@prefix, 'POL', @run_key);
SET @txn_normal_no := CONCAT(@prefix, 'TX', @run_key, '01');
SET @txn_large_no := CONCAT(@prefix, 'TX', @run_key, '02');
SET @txn_cross_no := CONCAT(@prefix, 'TX', @run_key, '03');
SET @txn_chain_01_no := CONCAT(@prefix, 'TX', @run_key, 'C01');
SET @txn_chain_02_no := CONCAT(@prefix, 'TX', @run_key, 'C02');
SET @txn_chain_03_no := CONCAT(@prefix, 'TX', @run_key, 'C03');
SET @txn_chain_04_no := CONCAT(@prefix, 'TX', @run_key, 'C04');
SET @txn_chain_05_no := CONCAT(@prefix, 'TX', @run_key, 'C05');
SET @txn_chain_06_no := CONCAT(@prefix, 'TX', @run_key, 'C06');
SET @rule_large_code := CONCAT(@prefix, 'RLG', @run_key);
SET @rule_struct_code := CONCAT(@prefix, 'RST', @run_key);
SET @rule_chain_code := CONCAT(@prefix, 'RCH', @run_key);
SET @product_name := '鑫享传世终身寿险（万能型）';
SET @beneficiary_name := '李安然';
SET @counterparty_normal_name := '北京恒信科技有限公司';
SET @counterparty_large_name := '上海鼎源珠宝贸易有限公司';
SET @counterparty_cross_name := 'Harbor Pacific Holdings Ltd.';
SET @normal_bank_name := '招商银行北京分行';
SET @large_bank_name := '中国工商银行上海分行';
SET @cross_bank_name := 'HSBC Hong Kong';
SET @chain_bank_name := '平安银行深圳前海支行';
SET @shared_chain_account := CONCAT('688866', RIGHT(@run_key, 8));

INSERT INTO t_product
  (product_code, product_name, product_type, product_sub_type, payment_mode, has_cash_value, has_investment_feature, surrender_flexibility, beneficiary_changeable, risk_level, risk_score, risk_factors, status, effective_date, created_by, created_time)
VALUES
  (@product_code, @product_name, 'LIFE', 'WHOLE_LIFE', 'FLEXIBLE', 1, 1, 'HIGH', 1, 'HIGH', 88, CONCAT('{"runId":"', @run_id, '","factors":["cash_value","investment","flexible_payment"]}'), 'ACTIVE', CURDATE(), @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  product_name = VALUES(product_name),
  risk_level = VALUES(risk_level),
  risk_score = VALUES(risk_score),
  risk_factors = VALUES(risk_factors),
  updated_by = @seed_by,
  updated_time = NOW();
SELECT @product_id := id FROM t_product WHERE product_code = @product_code LIMIT 1;

INSERT INTO t_product_risk_assessment
  (product_id, assessment_date, assessor, client_group_score, payment_mode_score, product_structure_score, surrender_score, beneficiary_score, channel_score, total_score, risk_level, assessment_result, approved_by, approved_time, status, created_by, created_time)
SELECT @product_id, CURDATE(), 'e2e_compliance', 80, 85, 88, 90, 75, 70, 82, 'HIGH', CONCAT(@prefix, @product_name, ' 产品风险评估 ', @run_id), 'e2e_seed_operator', NOW(), 'APPROVED', @seed_by, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_product_risk_assessment WHERE product_id = @product_id AND assessment_date = CURDATE() AND LOCATE(@run_id, COALESCE(assessment_result, '')) > 0
);
UPDATE t_product_risk_assessment
SET assessment_result = CONCAT(@prefix, @product_name, ' 产品风险评估 ', @run_id)
WHERE product_id = @product_id
  AND assessment_date = CURDATE()
  AND LOCATE(@run_id, COALESCE(assessment_result, '')) > 0;

INSERT INTO t_policy
  (policy_no, customer_id, product_id, sum_insured, premium, payment_mode, effective_date, expiry_date, policy_status, agent_code, channel, beneficiary_info, insured_name, insured_id_type, insured_id_number, remark, status, created_by, created_time)
VALUES
  (@policy_no, @normal_customer_id, @product_id, 1000000.00, 60000.00, 'FLEXIBLE', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 20 YEAR), 'ACTIVE', CONCAT(@prefix, 'AGT'), 'BANK_INSURANCE', JSON_OBJECT('name', @beneficiary_name, 'relationship', 'SPOUSE'), @normal_customer_name, 'IDCARD', CONCAT('11010119990101', RIGHT(@run_key, 4)), CONCAT(@prefix, '业务闭环保单 ', @run_id), 'ACTIVE', @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  customer_id = VALUES(customer_id),
  product_id = VALUES(product_id),
  premium = VALUES(premium),
  beneficiary_info = VALUES(beneficiary_info),
  insured_name = VALUES(insured_name),
  remark = VALUES(remark),
  updated_by = @seed_by,
  updated_time = NOW();
SELECT @policy_id := id FROM t_policy WHERE policy_no = @policy_no LIMIT 1;

INSERT INTO t_transaction
  (transaction_no, policy_id, customer_id, transaction_type, amount, currency, payment_method, channel, counterparty_name, counterparty_account, counterparty_bank, is_cross_border, transaction_time, remark, status, source_system, created_time)
VALUES
  (@txn_normal_no, @policy_id, @normal_customer_id, 'PREMIUM', 12000.00, 'CNY', 'TRANSFER', 'ONLINE', @counterparty_normal_name, CONCAT('622200', RIGHT(@run_key, 8)), @normal_bank_name, 0, CONCAT(CURDATE(), ' 09:00:00'), CONCAT(@prefix, '首期保费转账缴纳 ', @run_id), 'SUCCESS', 'E2E-SEED', NOW()),
  (@txn_large_no, @policy_id, @sanction_customer_id, 'PREMIUM', 260000.00, 'CNY', 'CASH', 'COUNTER', @counterparty_large_name, CONCAT('622201', RIGHT(@run_key, 8)), @large_bank_name, 0, CONCAT(CURDATE(), ' 10:00:00'), CONCAT(@prefix, '柜面大额现金缴费 ', @run_id), 'SUCCESS', 'E2E-SEED', NOW()),
  (@txn_cross_no, @policy_id, @corp_customer_id, 'SURRENDER', 320000.00, 'USD', 'TRANSFER', 'BANK_COUNTER', @counterparty_cross_name, CONCAT('998877', RIGHT(@run_key, 8)), @cross_bank_name, 1, CONCAT(CURDATE(), ' 11:00:00'), CONCAT(@prefix, '跨境退保资金划转 ', @run_id), 'SUCCESS', 'E2E-SEED', NOW()),
  (@txn_chain_01_no, @policy_id, @normal_customer_id, 'PREMIUM', 78000.00, 'CNY', 'TRANSFER', 'ONLINE', @chain_customer_01_name, @shared_chain_account, @chain_bank_name, 0, CONCAT(CURDATE(), ' 15:01:00'), CONCAT(@prefix, '复杂关联链路第1跳：', @normal_customer_name, ' -> ', @chain_customer_01_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW()),
  (@txn_chain_02_no, NULL, @chain_customer_01_id, 'LOAN', 76500.00, 'CNY', 'TRANSFER', 'BANK_COUNTER', @chain_customer_02_name, CONCAT('621701', RIGHT(@run_key, 8)), '兴业银行厦门分行', 0, CONCAT(CURDATE(), ' 15:08:00'), CONCAT(@prefix, '复杂关联链路第2跳：', @chain_customer_01_name, ' -> ', @chain_customer_02_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW()),
  (@txn_chain_03_no, NULL, @chain_customer_02_id, 'REPAYMENT', 74800.00, 'CNY', 'TRANSFER', 'BANK_COUNTER', @chain_customer_03_name, CONCAT('621702', RIGHT(@run_key, 8)), '宁波银行北仑支行', 0, CONCAT(CURDATE(), ' 15:15:00'), CONCAT(@prefix, '复杂关联链路第3跳：', @chain_customer_02_name, ' -> ', @chain_customer_03_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW()),
  (@txn_chain_04_no, NULL, @chain_customer_03_id, 'PARTIAL_WITHDRAWAL', 73500.00, 'CNY', 'TRANSFER', 'BANK_COUNTER', @corp_customer_name, CONCAT('621703', RIGHT(@run_key, 8)), '浦发银行上海分行', 0, CONCAT(CURDATE(), ' 15:22:00'), CONCAT(@prefix, '复杂关联链路第4跳：', @chain_customer_03_name, ' -> ', @corp_customer_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW()),
  (@txn_chain_05_no, NULL, @corp_customer_id, 'SURRENDER', 72000.00, 'USD', 'TRANSFER', 'BANK_COUNTER', @counterparty_cross_name, CONCAT('998800', RIGHT(@run_key, 8)), @cross_bank_name, 1, CONCAT(CURDATE(), ' 15:29:00'), CONCAT(@prefix, '复杂关联链路第5跳：', @corp_customer_name, ' -> ', @counterparty_cross_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW()),
  (@txn_chain_06_no, NULL, @corp_customer_id, 'CLAIM', 70200.00, 'CNY', 'TRANSFER', 'ONLINE', @normal_customer_name, @shared_chain_account, @normal_bank_name, 0, CONCAT(CURDATE(), ' 15:36:00'), CONCAT(@prefix, '复杂关联链路回流跳：', @corp_customer_name, ' -> ', @normal_customer_name, ' ', @run_id), 'SUCCESS', 'E2E-GRAPH-SEED', NOW())
ON DUPLICATE KEY UPDATE
  amount = VALUES(amount),
  payment_method = VALUES(payment_method),
  counterparty_name = VALUES(counterparty_name),
  counterparty_account = VALUES(counterparty_account),
  counterparty_bank = VALUES(counterparty_bank),
  is_cross_border = VALUES(is_cross_border),
  transaction_time = VALUES(transaction_time),
  source_system = VALUES(source_system),
  remark = VALUES(remark),
  updated_time = NOW();

SELECT @txn_normal_id := id FROM t_transaction WHERE transaction_no = @txn_normal_no LIMIT 1;
SELECT @txn_large_id := id FROM t_transaction WHERE transaction_no = @txn_large_no LIMIT 1;
SELECT @txn_cross_id := id FROM t_transaction WHERE transaction_no = @txn_cross_no LIMIT 1;
SELECT @txn_chain_01_id := id FROM t_transaction WHERE transaction_no = @txn_chain_01_no LIMIT 1;
SELECT @txn_chain_02_id := id FROM t_transaction WHERE transaction_no = @txn_chain_02_no LIMIT 1;
SELECT @txn_chain_03_id := id FROM t_transaction WHERE transaction_no = @txn_chain_03_no LIMIT 1;
SELECT @txn_chain_04_id := id FROM t_transaction WHERE transaction_no = @txn_chain_04_no LIMIT 1;
SELECT @txn_chain_05_id := id FROM t_transaction WHERE transaction_no = @txn_chain_05_no LIMIT 1;
SELECT @txn_chain_06_id := id FROM t_transaction WHERE transaction_no = @txn_chain_06_no LIMIT 1;

INSERT INTO t_transaction_daily_summary
  (customer_id, summary_date, transaction_type, payment_method, is_cross_border, total_amount, transaction_count, large_txn_flag, created_time)
VALUES
  (@normal_customer_id, CURDATE(), 'PREMIUM', 'TRANSFER', 0, 12000.00, 1, 0, NOW()),
  (@sanction_customer_id, CURDATE(), 'PREMIUM', 'CASH', 0, 260000.00, 1, 1, NOW()),
  (@corp_customer_id, CURDATE(), 'SURRENDER', 'TRANSFER', 1, 320000.00, 1, 1, NOW())
ON DUPLICATE KEY UPDATE
  total_amount = VALUES(total_amount),
  transaction_count = VALUES(transaction_count),
  large_txn_flag = VALUES(large_txn_flag),
  updated_time = NOW();

INSERT INTO t_rule_definition
  (rule_code, rule_name, rule_category, description, risk_weight, priority, status, effective_date, config_json, created_by, created_time)
VALUES
  (@rule_large_code, '大额现金缴费监测规则', 'LARGE_TXN', CONCAT(@prefix, '业务闭环大额现金缴费规则 ', @run_id), 90, 100, 'ENABLED', CURDATE(), '{"threshold":50000,"paymentMethod":"CASH"}', @seed_by, NOW()),
  (@rule_struct_code, '跨境退保异常监测规则', 'SUSPICIOUS', CONCAT(@prefix, '业务闭环跨境退保规则 ', @run_id), 85, 95, 'ENABLED', CURDATE(), '{"crossBorder":true,"transactionType":"SURRENDER"}', @seed_by, NOW()),
  (@rule_chain_code, '复杂资金链路关联监测规则', 'CORRELATION', CONCAT(@prefix, '多跳转账、金额递减、跨境转出及回流关联监测规则 ', @run_id), 92, 90, 'ENABLED', CURDATE(), CONCAT('{"minDepth":5,"amountDecay":true,"ringBack":true,"sharedAccount":"', @shared_chain_account, '"}'), @seed_by, NOW())
ON DUPLICATE KEY UPDATE
  rule_name = VALUES(rule_name),
  description = VALUES(description),
  config_json = VALUES(config_json),
  updated_by = @seed_by,
  updated_time = NOW();
SELECT @rule_large_id := id FROM t_rule_definition WHERE rule_code = @rule_large_code LIMIT 1;
SELECT @rule_struct_id := id FROM t_rule_definition WHERE rule_code = @rule_struct_code LIMIT 1;
SELECT @rule_chain_id := id FROM t_rule_definition WHERE rule_code = @rule_chain_code LIMIT 1;

INSERT INTO t_rule_execution_log
  (rule_id, rule_code, transaction_id, customer_id, execution_time, match_result, match_score, execution_detail, duration_ms, created_time)
SELECT rule_id, rule_code, transaction_id, customer_id, NOW(), 1, match_score, detail, duration_ms, NOW()
FROM (
  SELECT @rule_large_id AS rule_id, @rule_large_code AS rule_code, @txn_large_id AS transaction_id, @sanction_customer_id AS customer_id, 96.00 AS match_score, CONCAT('{"runId":"', @run_id, '","reason":"large_cash"}') AS detail, 18 AS duration_ms
  UNION ALL SELECT @rule_struct_id, @rule_struct_code, @txn_cross_id, @corp_customer_id, 89.00, CONCAT('{"runId":"', @run_id, '","reason":"cross_border_surrender"}'), 22
  UNION ALL SELECT @rule_chain_id, @rule_chain_code, @txn_chain_05_id, @corp_customer_id, 93.00, CONCAT('{"runId":"', @run_id, '","reason":"multi_hop_decay_cross_border","chainDepth":6}'), 31
  UNION ALL SELECT @rule_chain_id, @rule_chain_code, @txn_chain_06_id, @corp_customer_id, 91.00, CONCAT('{"runId":"', @run_id, '","reason":"ring_back_to_origin","sharedAccount":"', @shared_chain_account, '"}'), 28
) r
WHERE NOT EXISTS (
  SELECT 1 FROM t_rule_execution_log l WHERE l.rule_code = r.rule_code AND l.transaction_id = r.transaction_id
);

-- ---------------------------------------------------------------------------
-- 5. Alerts, cases, STR and large transaction reports
-- ---------------------------------------------------------------------------
SET @alert_large_no := CONCAT(@prefix, 'AL', @run_key, '01');
SET @alert_sanction_no := CONCAT(@prefix, 'AL', @run_key, '02');
SET @case_no := CONCAT(@prefix, 'CASE', @run_key);
SET @str_report_no := CONCAT(@prefix, 'STR', @run_key);
SET @large_report_no := CONCAT(@prefix, 'LTR', @run_key);

INSERT INTO t_alert
  (alert_no, customer_id, customer_name, alert_type, risk_score, risk_level, source_rule_codes, alert_summary, status, assigned_to, assigned_time, process_result, process_remark, process_time, deduplicate_key, related_transaction_ids, created_time)
VALUES
  (@alert_large_no, @sanction_customer_id, @sanction_customer_name, 'LARGE_TXN', 96, 'CRITICAL', @rule_large_code, CONCAT(@prefix, '客户柜面大额现金缴费触发预警 ', @run_id), 'CONFIRMED', @investigator_id, NOW(), 'CONFIRMED_SUSPICIOUS', CONCAT(@prefix, '复核确认资金来源说明不足'), NOW(), CONCAT(@prefix, ':', @run_key, ':LARGE'), CAST(@txn_large_id AS CHAR), NOW()),
  (@alert_sanction_no, @sanction_customer_id, @sanction_customer_name, 'SANCTIONS_HIT', 98, 'CRITICAL', 'SANCTIONS_SCREENING', CONCAT(@prefix, '客户命中国际制裁名单预警 ', @run_id), 'ESCALATED', @investigator_id, NOW(), 'ESCALATED', CONCAT(@prefix, '升级案件调查并暂停后续交易'), NOW(), CONCAT(@prefix, ':', @run_key, ':SANCTION'), CAST(@txn_large_id AS CHAR), NOW())
ON DUPLICATE KEY UPDATE
  customer_name = VALUES(customer_name),
  alert_summary = VALUES(alert_summary),
  status = VALUES(status),
  assigned_to = VALUES(assigned_to),
  process_result = VALUES(process_result),
  process_remark = VALUES(process_remark),
  related_transaction_ids = VALUES(related_transaction_ids),
  updated_time = NOW();
SELECT @alert_large_id := id FROM t_alert WHERE alert_no = @alert_large_no LIMIT 1;
SELECT @alert_sanction_id := id FROM t_alert WHERE alert_no = @alert_sanction_no LIMIT 1;

INSERT INTO t_alert_rule_detail
  (alert_id, rule_id, rule_code, rule_name, match_score, match_detail, created_time)
SELECT @alert_large_id, @rule_large_id, @rule_large_code, '大额现金缴费监测规则', 96.00, CONCAT('{"runId":"', @run_id, '","transactionNo":"', @txn_large_no, '"}'), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_alert_rule_detail WHERE alert_id = @alert_large_id AND rule_code = @rule_large_code);
UPDATE t_alert_rule_detail
SET rule_name = '大额现金缴费监测规则'
WHERE alert_id = @alert_large_id AND rule_code = @rule_large_code;

INSERT INTO t_alert_assignment_log
  (alert_id, from_user_id, to_user_id, assign_type, assign_reason, assigned_by, assigned_time)
SELECT @alert_sanction_id, @compliance_id, @investigator_id, 'MANUAL', CONCAT(@prefix, '名单命中叠加大额现金缴费，升级调查 ', @run_id), 'e2e_compliance', NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_alert_assignment_log WHERE alert_id = @alert_sanction_id AND to_user_id = @investigator_id);

INSERT INTO t_case
  (case_no, alert_id, customer_id, customer_name, case_status, case_type, priority, summary, investigator_id, reviewer_id, approver_id, submit_time, created_by, created_time)
VALUES
  (@case_no, @alert_sanction_id, @sanction_customer_id, @sanction_customer_name, 'SUBMITTED', 'SUSPICIOUS_TRANSACTION', 100, CONCAT(@prefix, '客户名单命中叠加大额现金缴费调查案件 ', @run_id), @investigator_id, @compliance_id, @operator_id, NOW(), '合规负责人', NOW())
ON DUPLICATE KEY UPDATE
  alert_id = VALUES(alert_id),
  customer_id = VALUES(customer_id),
  customer_name = VALUES(customer_name),
  case_status = VALUES(case_status),
  priority = VALUES(priority),
  summary = VALUES(summary),
  investigator_id = VALUES(investigator_id),
  reviewer_id = VALUES(reviewer_id),
  approver_id = VALUES(approver_id),
  submit_time = VALUES(submit_time),
  updated_by = @seed_by,
  updated_time = NOW();
SELECT @case_id := id FROM t_case WHERE case_no = @case_no LIMIT 1;

INSERT INTO t_case_status_log (case_id, from_status, to_status, remark, changed_by, changed_time)
SELECT @case_id, from_status, to_status, remark, changed_by, NOW()
FROM (
  SELECT NULL AS from_status, 'DRAFT' AS to_status, CONCAT(@prefix, '案件创建 ', @run_id) AS remark, '合规负责人' AS changed_by
  UNION ALL SELECT 'DRAFT', 'INVESTIGATING', CONCAT(@prefix, '进入调查 ', @run_id), '案件调查员'
  UNION ALL SELECT 'INVESTIGATING', 'SUBMITTED', CONCAT(@prefix, '提交报送 ', @run_id), '合规审批员'
) s
WHERE NOT EXISTS (
  SELECT 1 FROM t_case_status_log l WHERE l.case_id = @case_id AND l.to_status = s.to_status AND LOCATE(@run_id, COALESCE(l.remark, '')) > 0
);

INSERT INTO t_case_investigation
  (case_id, content, conclusion, investigator_id, created_time)
SELECT @case_id, CONCAT(@prefix, '调查记录：核查客户身份资料、名单命中证据、现金缴费来源及保单受益安排 ', @run_id), 'SUSPICIOUS_CONFIRMED', @investigator_id, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_case_investigation WHERE case_id = @case_id AND LOCATE(@run_id, content) > 0
);
UPDATE t_case_investigation
SET content = CONCAT(@prefix, '调查记录：核查客户身份资料、名单命中证据、现金缴费来源及保单受益安排 ', @run_id)
WHERE case_id = @case_id AND LOCATE(@run_id, content) > 0;

INSERT INTO t_str_report
  (report_no, case_id, customer_id, report_type, report_status, report_content, analysis_opinion, measures_taken, writer_id, writer_time, reviewer_id, reviewer_opinion, reviewer_time, approver_id, approver_opinion, approver_time, submit_time, submit_result, created_time)
VALUES
  (@str_report_no, @case_id, @sanction_customer_id, 'URGENT', 'SUBMITTED', CONCAT(@prefix, '可疑交易报告：客户 Grace Miller 命中国际制裁名单并发生柜面大额现金缴费，资金来源解释不足。', @run_id), '客户身份、名单命中和交易行为叠加呈现高风险特征，建议按可疑交易报送。', '已暂停后续高风险交易，要求补充资金来源材料并升级人工复核。', @compliance_id, NOW(), @compliance_id, '审核通过', NOW(), @operator_id, '签发通过', NOW(), NOW(), '{"status":"SUBMITTED","receiptStatus":"PENDING","seed":true}', NOW())
ON DUPLICATE KEY UPDATE
  case_id = VALUES(case_id),
  customer_id = VALUES(customer_id),
  report_status = VALUES(report_status),
  report_content = VALUES(report_content),
  analysis_opinion = VALUES(analysis_opinion),
  measures_taken = VALUES(measures_taken),
  submit_result = VALUES(submit_result),
  updated_time = NOW();
SELECT @str_report_id := id FROM t_str_report WHERE report_no = @str_report_no LIMIT 1;

INSERT INTO t_large_txn_report
  (report_no, customer_id, customer_name, transaction_id, report_date, transaction_time, transaction_type, amount, currency, payment_method, counterparty_info, report_status, reviewed_by, reviewed_time, submitted_by, submitted_time, xml_content, submit_response, created_time)
VALUES
  (@large_report_no, @sanction_customer_id, @sanction_customer_name, @txn_large_id, CURDATE(), CONCAT(CURDATE(), ' 10:00:00'), 'PREMIUM', 260000.00, 'CNY', 'CASH', CONCAT('{"counterparty":"', @counterparty_large_name, '","bank":"', @large_bank_name, '","runId":"', @run_id, '"}'), 'RESUBMITTED', '合规审批员', NOW(), '监管报送员', NOW(), NULL, '{"status":"ACCEPTED","versionNo":2,"seed":true}', NOW())
ON DUPLICATE KEY UPDATE
  customer_id = VALUES(customer_id),
  customer_name = VALUES(customer_name),
  transaction_id = VALUES(transaction_id),
  counterparty_info = VALUES(counterparty_info),
  report_status = VALUES(report_status),
  reviewed_by = VALUES(reviewed_by),
  reviewed_time = VALUES(reviewed_time),
  submitted_by = VALUES(submitted_by),
  submitted_time = VALUES(submitted_time),
  xml_content = VALUES(xml_content),
  submit_response = VALUES(submit_response),
  updated_time = NOW();
SELECT @large_report_id := id FROM t_large_txn_report WHERE report_no = @large_report_no LIMIT 1;

-- 监管报送版本链：大额报告首报退回后修正重报，STR保留等待回执场景。
SET @large_submission_no_v1 := CONCAT(@prefix, 'REG', @run_key, 'L01');
SET @large_submission_no_v2 := CONCAT(@prefix, 'REG', @run_key, 'L02');
SET @str_submission_no_v1 := CONCAT(@prefix, 'REG', @run_key, 'S01');
SET @large_payload_v1 := CONCAT('<?xml version="1.0" encoding="UTF-8"?><LargeTransactionReport><Header><ReportNo>', @large_report_no, '</ReportNo><ReportDate>', CURDATE(), '</ReportDate><InstitutionCode>INS001</InstitutionCode></Header><CustomerInfo><CustomerId>', @sanction_customer_id, '</CustomerId><CustomerName>Grace Miller</CustomerName></CustomerInfo><TransactionInfo><TransactionId>', @txn_large_id, '</TransactionId><Amount>260000.00</Amount><Currency>CNY</Currency></TransactionInfo><SeedRunId>', @run_id, '</SeedRunId></LargeTransactionReport>');
SET @large_payload_v2 := REPLACE(@large_payload_v1, '</LargeTransactionReport>', '<CorrectionNote>补充交易对手开户行及资金来源说明</CorrectionNote></LargeTransactionReport>');
SET @str_payload_v1 := CONCAT('<?xml version="1.0" encoding="UTF-8"?><SuspiciousTransactionReport><Header><ReportNo>', @str_report_no, '</ReportNo><InstitutionCode>INS001</InstitutionCode></Header><CaseInfo><CaseId>', @case_id, '</CaseId><CustomerId>', @sanction_customer_id, '</CustomerId></CaseInfo><ReportBody><Content>制裁名单命中叠加大额现金缴费</Content><AnalysisOpinion>建议持续增强监测并报送</AnalysisOpinion></ReportBody><SeedRunId>', @run_id, '</SeedRunId></SuspiciousTransactionReport>');

INSERT INTO t_regulatory_submission
  (id, submission_no, report_type, report_id, report_no, version_no, parent_submission_id,
   connector_id, status, schema_version, payload_format, payload_content, payload_hash,
   signature_algorithm, signature_value, external_request_id, submitted_by, submitted_time,
   completed_time, receipt_status, receipt_no, receipt_time, return_code, return_message,
   correction_note, retry_count, created_by, created_time, updated_by, updated_time)
SELECT UUID_SHORT(), @large_submission_no_v1, 'LARGE_TXN', @large_report_id, @large_report_no, 1, NULL,
       @connector_regulator_id, 'REJECTED', 'AML-MIS-LOCAL-1.0', 'XML', @large_payload_v1,
       SHA2(@large_payload_v1, 256), 'SHA-256-MOCK', SHA2(@large_payload_v1, 256),
       CONCAT(@prefix, 'REQ', @run_key, 'L01'), 'e2e_compliance', DATE_SUB(NOW(), INTERVAL 25 MINUTE),
       DATE_SUB(NOW(), INTERVAL 24 MINUTE), 'REJECTED', CONCAT(@prefix, 'RCPT', @run_key, 'L01'),
       DATE_SUB(NOW(), INTERVAL 24 MINUTE), 'E102', '交易对手开户行和资金来源说明不完整',
       NULL, 0, @seed_by, DATE_SUB(NOW(), INTERVAL 25 MINUTE), @seed_by, DATE_SUB(NOW(), INTERVAL 24 MINUTE)
WHERE NOT EXISTS (SELECT 1 FROM t_regulatory_submission WHERE submission_no = @large_submission_no_v1);
SELECT @large_submission_v1_id := id FROM t_regulatory_submission WHERE submission_no = @large_submission_no_v1 LIMIT 1;

INSERT INTO t_regulatory_submission
  (id, submission_no, report_type, report_id, report_no, version_no, parent_submission_id,
   connector_id, status, schema_version, payload_format, payload_content, payload_hash,
   signature_algorithm, signature_value, external_request_id, submitted_by, submitted_time,
   completed_time, receipt_status, receipt_no, receipt_time, return_code, return_message,
   correction_note, retry_count, created_by, created_time, updated_by, updated_time)
SELECT UUID_SHORT(), @large_submission_no_v2, 'LARGE_TXN', @large_report_id, @large_report_no, 2, @large_submission_v1_id,
       @connector_regulator_id, 'ACCEPTED', 'AML-MIS-LOCAL-1.0', 'XML', @large_payload_v2,
       SHA2(@large_payload_v2, 256), 'SHA-256-MOCK', SHA2(@large_payload_v2, 256),
       CONCAT(@prefix, 'REQ', @run_key, 'L02'), 'e2e_compliance', DATE_SUB(NOW(), INTERVAL 15 MINUTE),
       DATE_SUB(NOW(), INTERVAL 14 MINUTE), 'ACCEPTED', CONCAT(@prefix, 'RCPT', @run_key, 'L02'),
       DATE_SUB(NOW(), INTERVAL 14 MINUTE), '0000', '报文校验通过并接收',
       '补充交易对手开户行及资金来源说明', 1, @seed_by, DATE_SUB(NOW(), INTERVAL 15 MINUTE), @seed_by, DATE_SUB(NOW(), INTERVAL 14 MINUTE)
WHERE NOT EXISTS (SELECT 1 FROM t_regulatory_submission WHERE submission_no = @large_submission_no_v2);
SELECT @large_submission_v2_id := id FROM t_regulatory_submission WHERE submission_no = @large_submission_no_v2 LIMIT 1;

INSERT INTO t_regulatory_submission
  (id, submission_no, report_type, report_id, report_no, version_no, parent_submission_id,
   connector_id, status, schema_version, payload_format, payload_content, payload_hash,
   signature_algorithm, signature_value, external_request_id, submitted_by, submitted_time,
   receipt_status, receipt_time, return_code, return_message, retry_count,
   created_by, created_time, updated_by, updated_time)
SELECT UUID_SHORT(), @str_submission_no_v1, 'SUSPICIOUS', @str_report_id, @str_report_no, 1, NULL,
       @connector_regulator_id, 'SUBMITTED', 'AML-MIS-LOCAL-1.0', 'XML', @str_payload_v1,
       SHA2(@str_payload_v1, 256), 'SHA-256-MOCK', SHA2(@str_payload_v1, 256),
       CONCAT(@prefix, 'REQ', @run_key, 'S01'), 'e2e_compliance', DATE_SUB(NOW(), INTERVAL 5 MINUTE),
       'PENDING', DATE_SUB(NOW(), INTERVAL 5 MINUTE), 'PENDING', '监管平台处理中', 0,
       @seed_by, DATE_SUB(NOW(), INTERVAL 5 MINUTE), @seed_by, DATE_SUB(NOW(), INTERVAL 5 MINUTE)
WHERE NOT EXISTS (SELECT 1 FROM t_regulatory_submission WHERE submission_no = @str_submission_no_v1);
SELECT @str_submission_v1_id := id FROM t_regulatory_submission WHERE submission_no = @str_submission_no_v1 LIMIT 1;

INSERT INTO t_regulatory_receipt
  (id, submission_id, receipt_no, receipt_status, receipt_code, receipt_message,
   receipt_payload, received_time, receipt_source, created_by, created_time, updated_by, updated_time)
SELECT UUID_SHORT(), r.submission_id, r.receipt_no, r.receipt_status, r.receipt_code, r.receipt_message,
       r.receipt_payload, r.received_time, 'GATEWAY', @seed_by, r.received_time, @seed_by, r.received_time
FROM (
  SELECT @large_submission_v1_id AS submission_id, CONCAT(@prefix, 'RCPT', @run_key, 'L01') AS receipt_no,
         'REJECTED' AS receipt_status, 'E102' AS receipt_code, '交易对手开户行和资金来源说明不完整' AS receipt_message,
         CONCAT('{"status":"REJECTED","runId":"', @run_id, '"}') AS receipt_payload,
         DATE_SUB(NOW(), INTERVAL 24 MINUTE) AS received_time
  UNION ALL SELECT @large_submission_v2_id, CONCAT(@prefix, 'RCPT', @run_key, 'L02'),
         'ACCEPTED', '0000', '报文校验通过并接收', CONCAT('{"status":"ACCEPTED","runId":"', @run_id, '"}'), DATE_SUB(NOW(), INTERVAL 14 MINUTE)
  UNION ALL SELECT @str_submission_v1_id, NULL,
         'PENDING', 'PENDING', '监管平台处理中', CONCAT('{"status":"PENDING","runId":"', @run_id, '"}'), DATE_SUB(NOW(), INTERVAL 5 MINUTE)
) r
WHERE NOT EXISTS (
  SELECT 1 FROM t_regulatory_receipt x
  WHERE x.submission_id = r.submission_id AND x.receipt_status = r.receipt_status AND x.receipt_source = 'GATEWAY'
);

UPDATE t_large_txn_report
SET report_status = 'RESUBMITTED', xml_content = @large_payload_v2,
    submit_response = CONCAT('{"status":"ACCEPTED","submissionNo":"', @large_submission_no_v2, '","versionNo":2,"seed":true}'),
    updated_time = NOW()
WHERE id = @large_report_id;

UPDATE t_str_report
SET report_status = 'SUBMITTED',
    submit_result = CONCAT('{"status":"SUBMITTED","submissionNo":"', @str_submission_no_v1, '","receiptStatus":"PENDING","seed":true}'),
    updated_time = NOW()
WHERE id = @str_report_id;

INSERT INTO t_report_submit_log
  (report_type, report_id, submission_id, submit_time, submit_status, request_data, response_data,
   external_request_id, receipt_no, retry_count, max_retries, created_time)
SELECT report_type, report_id, submission_id, submit_time, submit_status, request_data, response_data,
       external_request_id, receipt_no, retry_count, 3, submit_time
FROM (
  SELECT 'LARGE_TXN' AS report_type, @large_report_id AS report_id, @large_submission_v1_id AS submission_id,
         DATE_SUB(NOW(), INTERVAL 25 MINUTE) AS submit_time, 'FAILED' AS submit_status,
         @large_payload_v1 AS request_data, '{"status":"REJECTED","code":"E102","seed":true}' AS response_data,
         CONCAT(@prefix, 'REQ', @run_key, 'L01') AS external_request_id,
         CONCAT(@prefix, 'RCPT', @run_key, 'L01') AS receipt_no, 0 AS retry_count
  UNION ALL SELECT 'LARGE_TXN', @large_report_id, @large_submission_v2_id,
         DATE_SUB(NOW(), INTERVAL 15 MINUTE), 'SUCCESS', @large_payload_v2,
         '{"status":"ACCEPTED","code":"0000","seed":true}', CONCAT(@prefix, 'REQ', @run_key, 'L02'),
         CONCAT(@prefix, 'RCPT', @run_key, 'L02'), 1
  UNION ALL SELECT 'SUSPICIOUS', @str_report_id, @str_submission_v1_id,
         DATE_SUB(NOW(), INTERVAL 5 MINUTE), 'SUCCESS', @str_payload_v1,
         '{"status":"SUBMITTED","receiptStatus":"PENDING","seed":true}', CONCAT(@prefix, 'REQ', @run_key, 'S01'),
         NULL, 0
) l
WHERE NOT EXISTS (
  SELECT 1 FROM t_report_submit_log s WHERE s.submission_id = l.submission_id
);

-- ---------------------------------------------------------------------------
-- 6. Self-assessment, rectification, audit and notification data
-- ---------------------------------------------------------------------------
SET @indicator_inherent_code := CONCAT(@prefix, 'IND', @run_key, '01');
SET @indicator_control_code := CONCAT(@prefix, 'IND', @run_key, '02');

INSERT INTO t_self_assessment
  (assessment_year, assessment_period, assessment_status, assessor_id, inherent_risk_score, control_effectiveness_score, overall_score, overall_risk_level, conclusion, approved_by, approved_time, created_time)
SELECT YEAR(CURDATE()), 'ANNUAL', 'APPROVED', @compliance_id, 78, 64, 71, 'MEDIUM', CONCAT(@prefix, '年度反洗钱风险自评估：客户尽调、名单筛查和交易监测控制整体有效 ', @run_id), '合规负责人', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_self_assessment WHERE LOCATE(@run_id, COALESCE(conclusion, '')) > 0
);
SELECT @assessment_id := id FROM t_self_assessment WHERE LOCATE(@run_id, COALESCE(conclusion, '')) > 0 LIMIT 1;
UPDATE t_self_assessment
SET conclusion = CONCAT(@prefix, '年度反洗钱风险自评估：客户尽调、名单筛查和交易监测控制整体有效 ', @run_id),
    approved_by = '合规负责人',
    approved_time = COALESCE(approved_time, NOW())
WHERE id = @assessment_id;

INSERT INTO t_assessment_indicator
  (indicator_code, indicator_name, category, dimension, weight, scoring_criteria, max_score, status, created_time)
VALUES
  (@indicator_inherent_code, '客户与交易固有风险指标', 'INHERENT_RISK', '客户和交易风险', 50.00, '高风险客户占比、跨境交易占比、大额现金交易占比', 100, 'ENABLED', NOW()),
  (@indicator_control_code, '监测报送控制有效性指标', 'CONTROL_EFFECTIVENESS', '监测和报送控制', 50.00, '预警处理及时性、报送准确性、审计完整性', 100, 'ENABLED', NOW())
ON DUPLICATE KEY UPDATE
  indicator_name = VALUES(indicator_name),
  scoring_criteria = VALUES(scoring_criteria),
  updated_time = NOW();
SELECT @indicator_inherent_id := id FROM t_assessment_indicator WHERE indicator_code = @indicator_inherent_code LIMIT 1;
SELECT @indicator_control_id := id FROM t_assessment_indicator WHERE indicator_code = @indicator_control_code LIMIT 1;

INSERT INTO t_assessment_score
  (assessment_id, indicator_id, raw_value, score, evidence, data_source, remark, scored_by, scored_time, created_time)
SELECT @assessment_id, indicator_id, raw_value, score, evidence, 'E2E-SEED', CONCAT(@prefix, '评分样本 ', @run_id), 'e2e_compliance', NOW(), NOW()
FROM (
  SELECT @indicator_inherent_id AS indicator_id, 78.00 AS raw_value, 78 AS score, '高风险客户占比、跨境交易和大额现金交易综合证据' AS evidence
  UNION ALL SELECT @indicator_control_id, 64.00, 64, '预警处理时效、案件调查完整性和报送准确性证据'
) s
WHERE NOT EXISTS (
  SELECT 1 FROM t_assessment_score WHERE assessment_id = @assessment_id AND indicator_id = s.indicator_id
);

INSERT INTO t_rectification_task
  (assessment_id, issue_description, severity, responsible_dept, responsible_person, deadline, status, completion_evidence, completed_time, verified_by, verified_time, created_time)
SELECT @assessment_id, '高风险客户复核频率不足，名单筛查复核记录需补充。', 'MEDIUM', '合规部', '赵清妍', DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'IN_PROGRESS', NULL, NULL, NULL, NULL, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_rectification_task WHERE assessment_id = @assessment_id
);
UPDATE t_rectification_task
SET issue_description = '高风险客户复核频率不足，名单筛查复核记录需补充。',
    responsible_person = '赵清妍'
WHERE assessment_id = @assessment_id
  AND (responsible_person LIKE 'e2e!_%' ESCAPE '!' OR issue_description LIKE CONCAT(@prefix, '%'));

INSERT INTO t_notification
  (user_id, type, title, content, related_type, related_id, is_read, created_time)
SELECT @investigator_id, 'ALERT', CONCAT(@prefix, 'Grace Miller 制裁名单命中预警待处理 ', @run_id), CONCAT(@prefix, 'Grace Miller 已命中国际制裁名单并发生柜面大额现金缴费，请完成案件调查。'), 'ALERT', CAST(@alert_sanction_id AS CHAR), 0, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_notification WHERE user_id = @investigator_id AND title = CONCAT(@prefix, 'Grace Miller 制裁名单命中预警待处理 ', @run_id)
);
UPDATE t_notification
SET title = CONCAT(@prefix, 'Grace Miller 制裁名单命中预警待处理 ', @run_id),
    content = CONCAT(@prefix, 'Grace Miller 已命中国际制裁名单并发生柜面大额现金缴费，请完成案件调查。')
WHERE user_id = @investigator_id
  AND related_type = 'ALERT'
  AND related_id = CAST(@alert_sanction_id AS CHAR);

INSERT INTO t_audit_log
  (trace_id, user_id, username, operation_type, module, target_type, target_id, detail, ip_address, user_agent, request_uri, request_method, response_code, duration_ms, created_time)
SELECT CONCAT(@prefix, '-SEED-', @run_key), @operator_id, 'e2e_seed_operator', 'IMPORT', 'SYSTEM', 'E2E_BUSINESS_DATA', @run_key, CONCAT(@prefix, '业务闭环种子导入 ', @run_id), '127.0.0.1', 'seed-e2e-business-data.sh', '/scripts/seed-e2e-business-data.sh', 'CLI', 200, 1, NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM t_audit_log WHERE trace_id = CONCAT(@prefix, '-SEED-', @run_key)
);

COMMIT;
SQL

read -r -d '' VERIFY_SQL <<SQL || true
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET collation_connection = 'utf8mb4_unicode_ci';
SET @prefix := '${PREFIX_SQL}';
SET @run_id := '${RUN_ID_SQL}';
SET @run_key := '${RUN_KEY_SQL}';
SET @assessment_id := (
  SELECT id FROM t_self_assessment WHERE LOCATE(@run_id, COALESCE(conclusion, '')) > 0 LIMIT 1
);

SELECT 'users' AS artifact, COUNT(*) AS seeded_rows
FROM t_user
WHERE username IN ('e2e_seed_operator', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer')
UNION ALL
SELECT 'organizations', COUNT(*) FROM t_aml_organization WHERE org_code IN (CONCAT(@prefix, 'ORGHQ', @run_key), CONCAT(@prefix, 'ORGBR', @run_key), CONCAT(@prefix, 'ORGOT', @run_key))
UNION ALL
SELECT 'organization_persons', COUNT(*) FROM t_aml_org_person WHERE organization_id IN (
  SELECT id FROM t_aml_organization WHERE org_code IN (CONCAT(@prefix, 'ORGHQ', @run_key), CONCAT(@prefix, 'ORGBR', @run_key), CONCAT(@prefix, 'ORGOT', @run_key))
)
UNION ALL
SELECT 'organization_shareholders', COUNT(*) FROM t_aml_org_shareholder WHERE organization_id = (
  SELECT id FROM t_aml_organization WHERE org_code = CONCAT(@prefix, 'ORGHQ', @run_key) LIMIT 1
)
UNION ALL
SELECT 'organization_registrations', COUNT(*) FROM t_aml_org_registration WHERE registration_no IN (CONCAT(@prefix, 'ORGREGHQ', @run_key), CONCAT(@prefix, 'ORGREGBR', @run_key), CONCAT(@prefix, 'ORGREGOT', @run_key))
UNION ALL
SELECT 'integration_connectors', COUNT(*) FROM t_integration_connector WHERE connector_code IN (CONCAT(@prefix, 'INTCORE', @run_key), CONCAT(@prefix, 'INTWL', @run_key), CONCAT(@prefix, 'INTREG', @run_key), CONCAT(@prefix, 'INTID', @run_key))
UNION ALL
SELECT 'integration_jobs', COUNT(*) FROM t_integration_job WHERE job_code IN (CONCAT(@prefix, 'JOBTX', @run_key), CONCAT(@prefix, 'JOBWL', @run_key), CONCAT(@prefix, 'JOBREG', @run_key), CONCAT(@prefix, 'JOBID', @run_key))
UNION ALL
SELECT 'integration_runs', COUNT(*) FROM t_integration_run WHERE run_no IN (CONCAT(@prefix, 'INTRUN', @run_key, '01'), CONCAT(@prefix, 'INTRUN', @run_key, '02'), CONCAT(@prefix, 'INTRUN', @run_key, '03'))
UNION ALL
SELECT 'customers', COUNT(*) FROM t_customer WHERE customer_no IN (CONCAT(@prefix, 'C', @run_key, '01'), CONCAT(@prefix, 'C', @run_key, '02'), CONCAT(@prefix, 'C', @run_key, '03'), CONCAT(@prefix, 'C', @run_key, '04'))
UNION ALL
SELECT 'complex_graph_customers', COUNT(*) FROM t_customer WHERE customer_no IN (CONCAT(@prefix, 'C', @run_key, '05'), CONCAT(@prefix, 'C', @run_key, '06'), CONCAT(@prefix, 'C', @run_key, '07'))
UNION ALL
SELECT 'watchlist_sources', COUNT(*) FROM t_watchlist_source WHERE source_code = CONCAT(@prefix, 'SRC', @run_key)
UNION ALL
SELECT 'watchlists', COUNT(*) FROM t_watchlist WHERE external_id = CONCAT(@prefix, 'WL', @run_key, '01')
UNION ALL
SELECT 'screening_requests', COUNT(*) FROM t_screening_request WHERE request_no = CONCAT(@prefix, 'SCR', @run_key, '01')
UNION ALL
SELECT 'screening_results', COUNT(*) FROM t_screening_result WHERE match_detail LIKE CONCAT('%', @run_id, '%')
UNION ALL
SELECT 'products', COUNT(*) FROM t_product WHERE product_code = CONCAT(@prefix, 'P', @run_key)
UNION ALL
SELECT 'policies', COUNT(*) FROM t_policy WHERE policy_no = CONCAT(@prefix, 'POL', @run_key)
UNION ALL
SELECT 'transactions', COUNT(*) FROM t_transaction WHERE transaction_no IN (CONCAT(@prefix, 'TX', @run_key, '01'), CONCAT(@prefix, 'TX', @run_key, '02'), CONCAT(@prefix, 'TX', @run_key, '03'))
UNION ALL
SELECT 'complex_graph_transactions', COUNT(*) FROM t_transaction WHERE transaction_no IN (CONCAT(@prefix, 'TX', @run_key, 'C01'), CONCAT(@prefix, 'TX', @run_key, 'C02'), CONCAT(@prefix, 'TX', @run_key, 'C03'), CONCAT(@prefix, 'TX', @run_key, 'C04'), CONCAT(@prefix, 'TX', @run_key, 'C05'), CONCAT(@prefix, 'TX', @run_key, 'C06'))
UNION ALL
SELECT 'rules', COUNT(*) FROM t_rule_definition WHERE rule_code IN (CONCAT(@prefix, 'RLG', @run_key), CONCAT(@prefix, 'RST', @run_key))
UNION ALL
SELECT 'complex_graph_rules', COUNT(*) FROM t_rule_definition WHERE rule_code = CONCAT(@prefix, 'RCH', @run_key)
UNION ALL
SELECT 'alerts', COUNT(*) FROM t_alert WHERE alert_no IN (CONCAT(@prefix, 'AL', @run_key, '01'), CONCAT(@prefix, 'AL', @run_key, '02'))
UNION ALL
SELECT 'cases', COUNT(*) FROM t_case WHERE case_no = CONCAT(@prefix, 'CASE', @run_key)
UNION ALL
SELECT 'str_reports', COUNT(*) FROM t_str_report WHERE report_no = CONCAT(@prefix, 'STR', @run_key)
UNION ALL
SELECT 'large_txn_reports', COUNT(*) FROM t_large_txn_report WHERE report_no = CONCAT(@prefix, 'LTR', @run_key)
UNION ALL
SELECT 'regulatory_submissions', COUNT(*) FROM t_regulatory_submission WHERE submission_no IN (CONCAT(@prefix, 'REG', @run_key, 'L01'), CONCAT(@prefix, 'REG', @run_key, 'L02'), CONCAT(@prefix, 'REG', @run_key, 'S01'))
UNION ALL
SELECT 'regulatory_receipts', COUNT(*) FROM t_regulatory_receipt WHERE submission_id IN (
  SELECT id FROM t_regulatory_submission WHERE submission_no IN (CONCAT(@prefix, 'REG', @run_key, 'L01'), CONCAT(@prefix, 'REG', @run_key, 'L02'), CONCAT(@prefix, 'REG', @run_key, 'S01'))
)
UNION ALL
SELECT 'self_assessments', COUNT(*) FROM t_self_assessment WHERE conclusion LIKE CONCAT('%', @run_id, '%')
UNION ALL
SELECT 'rectification_tasks', COUNT(*) FROM t_rectification_task WHERE assessment_id = @assessment_id
UNION ALL
SELECT 'notifications', COUNT(*) FROM t_notification WHERE title = CONCAT(@prefix, 'Grace Miller 制裁名单命中预警待处理 ', @run_id)
UNION ALL
SELECT 'audit_logs', COUNT(*) FROM t_audit_log WHERE trace_id = CONCAT(@prefix, '-SEED-', @run_key);
SQL

if [ "$SQL_ONLY" = true ]; then
    printf "%s\n" "$SEED_SQL"
    exit 0
fi

echo "=========================================="
echo "  AML E2E 业务数据种子"
echo "=========================================="
echo "  DB: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  Prefix: ${E2E_PREFIX}"
echo "  Run ID: ${E2E_RUN_ID}"
echo "  Run Key: ${RUN_KEY}"
echo "  Mode: ${MODE}"
echo ""

if [ "$EXECUTE" = true ]; then
    run_sql "$SEED_SQL" quiet
    echo ""
    echo "Seed completed. Verification:"
    run_sql "$VERIFY_SQL" print
    exit 0
fi

if [ "$VERIFY" = true ]; then
    run_sql "$VERIFY_SQL" print
    exit 0
else
    echo "Dry-run only. No data was written."
    echo ""
    echo "This run will create/update a full E2E business data bundle for:"
    echo "  Organizations: ${E2E_PREFIX}ORGHQ${RUN_KEY}, ${E2E_PREFIX}ORGBR${RUN_KEY}, ${E2E_PREFIX}ORGOT${RUN_KEY}"
    echo "  Integrations: 4 connectors, 4 jobs and 3 run records for ${RUN_KEY}"
    echo "  Customers: ${E2E_PREFIX}C${RUN_KEY}01..04"
    echo "  Transactions: ${E2E_PREFIX}TX${RUN_KEY}01..03 plus complex graph chain ${E2E_PREFIX}TX${RUN_KEY}C01..C06"
    echo "  Alerts: ${E2E_PREFIX}AL${RUN_KEY}01..02"
    echo "  Case: ${E2E_PREFIX}CASE${RUN_KEY}"
    echo "  Reports: ${E2E_PREFIX}STR${RUN_KEY}, ${E2E_PREFIX}LTR${RUN_KEY}"
    echo ""
    echo "Run with --execute --verify to write and verify data, or --sql-only to export SQL."
fi
