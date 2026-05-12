#!/bin/bash
# ============================================================================
# AML System - legacy mock data replacement
# Replaces mojibake text in old mock rows with readable Chinese labels.
# Default mode is dry-run; pass --execute to update the database.
# ============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-aml_system}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-aml_dev_123}"
EXECUTE=false
VERIFY=false

usage() {
    cat <<'EOF'
Usage:
  scripts/replace-legacy-mock-data.sh [--execute] [--verify]

Options:
  --execute      Replace legacy mojibake mock data. Without this flag, only dry-run counts are printed.
  --verify       Print remaining mojibake counts after dry-run or execution.
  -h, --help     Show help.

Environment:
  DB_HOST        Default 127.0.0.1
  DB_PORT        Default 3306
  DB_NAME        Default aml_system
  DB_USER        Default root
  DB_PASSWORD    Default aml_dev_123
  JAVA_HOME      Optional. Defaults to Homebrew OpenJDK 21 when available.
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

if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi

JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

if ! command -v "$JAVAC" >/dev/null 2>&1 || ! command -v "$JAVA" >/dev/null 2>&1; then
    echo "Java compiler/runtime not found. Install JDK 21 or set JAVA_HOME." >&2
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

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

cat > "$WORK_DIR/LegacyMockDataTool.java" <<'JAVA'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LegacyMockDataTool {
    record Fix(String name, String countSql, String updateSql) {}
    record Verify(String name, String countSql) {}

    private static final String[] MOJIBAKE_MARKERS = {"å", "æ", "è", "é", "ç", "ä"};

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            throw new IllegalArgumentException("Expected args: host port db user password mode verify");
        }
        String host = args[0];
        String port = args[1];
        String db = args[2];
        String user = args[3];
        String password = args[4];
        boolean execute = "execute".equals(args[5]);
        boolean verify = args.length > 6 && "verify".equals(args[6]);

        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            List<Fix> fixes = fixes();

            System.out.println("==========================================");
            System.out.println("  AML legacy mock data replacement");
            System.out.println("==========================================");
            System.out.println("  DB: " + user + "@" + host + ":" + port + "/" + db);
            System.out.println("  Mode: " + (execute ? "execute" : "dry-run"));
            System.out.println();

            for (Fix fix : fixes) {
                long count = scalar(connection, fix.countSql());
                System.out.printf("%-28s %d%n", fix.name(), count);
                if (execute && count > 0) {
                    int updated = update(connection, fix.updateSql());
                    System.out.printf("  updated: %d%n", updated);
                }
            }

            if (execute) {
                connection.commit();
                System.out.println();
                System.out.println("Replacement completed.");
            } else {
                connection.rollback();
                System.out.println();
                System.out.println("Dry-run only. Re-run with --execute to update these rows.");
            }

            if (verify) {
                System.out.println();
                System.out.println("Remaining mojibake counts:");
                for (Verify item : verifications()) {
                    System.out.printf("%-28s %d%n", item.name(), scalar(connection, item.countSql()));
                }
                printRemainingSamples(connection);
            }
        }
    }

    private static List<Fix> fixes() {
        List<Fix> fixes = new ArrayList<>();

        String customerWhere = anyMojibake("name", "address", "residence_address", "occupation", "employer", "job_title", "remark");
        fixes.add(new Fix(
                "customers",
                "SELECT COUNT(*) FROM t_customer WHERE " + customerWhere,
                """
                UPDATE t_customer
                SET name = CASE
                      WHEN """ + anyMojibake("name") + """
                        THEN CONCAT('测试客户_', id)
                      ELSE name
                    END,
                    address = CASE
                      WHEN """ + anyMojibake("address") + """
                        THEN '测试地址'
                      ELSE address
                    END,
                    residence_address = CASE
                      WHEN """ + anyMojibake("residence_address") + """
                        THEN '测试地址'
                      ELSE residence_address
                    END,
                    occupation = CASE
                      WHEN """ + anyMojibake("occupation") + """
                        THEN '测试职业'
                      ELSE occupation
                    END,
                    employer = CASE
                      WHEN """ + anyMojibake("employer") + """
                        THEN '测试单位'
                      ELSE employer
                    END,
                    job_title = CASE
                      WHEN """ + anyMojibake("job_title") + """
                        THEN '测试职务'
                      ELSE job_title
                    END,
                    remark = CASE
                      WHEN """ + anyMojibake("remark") + """
                        THEN '测试客户样本'
                      ELSE remark
                    END
                WHERE """ + customerWhere));

        String alertWhere = anyMojibake("customer_name", "alert_summary");
        fixes.add(new Fix(
                "alerts",
                "SELECT COUNT(*) FROM t_alert WHERE " + alertWhere,
                """
                UPDATE t_alert
                SET customer_name = CASE
                      WHEN alert_no REGEXP '^ALERT[0-9]+$'
                        THEN CONCAT('测试客户_', CAST(RIGHT(alert_no, 6) AS UNSIGNED))
                      ELSE CONCAT('测试客户_', id)
                    END,
                    alert_summary = CONCAT(
                      '测试预警#',
                      CASE
                        WHEN alert_no REGEXP '^ALERT[0-9]+$' THEN CAST(RIGHT(alert_no, 6) AS UNSIGNED)
                        ELSE id
                      END,
                      ' - ',
                      CASE alert_type
                        WHEN 'SANCTIONS_HIT' THEN '制裁命中'
                        WHEN 'LARGE_TXN' THEN '大额交易'
                        WHEN 'CROSS_BORDER' THEN '跨境交易'
                        WHEN 'FREQUENT_TXN' THEN '频繁交易'
                        WHEN 'SUSPICIOUS' THEN '可疑交易'
                        WHEN 'PEP_HIT' THEN 'PEP命中'
                        WHEN 'MANUAL' THEN '人工创建'
                        ELSE COALESCE(alert_type, '测试类型')
                      END
                    )
                WHERE """ + alertWhere));

        String caseWhere = anyMojibake("c.customer_name", "c.summary", "c.close_reason");
        fixes.add(new Fix(
                "cases",
                "SELECT COUNT(*) FROM t_case c WHERE " + caseWhere,
                """
                UPDATE t_case c
                LEFT JOIN t_customer cu ON cu.id = c.customer_id
                SET c.customer_name = CASE
                      WHEN """ + anyMojibake("c.customer_name") + """
                        THEN COALESCE(cu.name, CONCAT('测试客户_', c.customer_id))
                      ELSE c.customer_name
                    END,
                    c.summary = CASE
                      WHEN """ + anyMojibake("c.summary") + """
                        THEN CONCAT('测试案件#', c.id, ' - 预警调查闭环')
                      ELSE c.summary
                    END,
                    c.close_reason = CASE
                      WHEN """ + anyMojibake("c.close_reason") + """
                        THEN '测试案件关闭原因'
                      ELSE c.close_reason
                    END
                WHERE """ + caseWhere));

        String screeningWhere = anyMojibake("sr.customer_name", "sr.watchlist_name", "sr.review_reason");
        fixes.add(new Fix(
                "screening_results",
                "SELECT COUNT(*) FROM t_screening_result sr WHERE " + screeningWhere,
                """
                UPDATE t_screening_result sr
                LEFT JOIN t_customer cu ON cu.id = sr.customer_id
                SET sr.customer_name = CASE
                      WHEN """ + anyMojibake("sr.customer_name") + """
                        THEN COALESCE(cu.name, CONCAT('测试客户_', sr.customer_id))
                      ELSE sr.customer_name
                    END,
                    sr.watchlist_name = CASE
                      WHEN """ + anyMojibake("sr.watchlist_name") + """
                        THEN CONCAT('测试名单对象_', sr.id)
                      ELSE sr.watchlist_name
                    END,
                    sr.review_reason = CASE
                      WHEN """ + anyMojibake("sr.review_reason") + """
                        THEN '测试复核说明'
                      ELSE sr.review_reason
                    END
                WHERE """ + screeningWhere));

        String whitelistWhere = anyMojibake("w.customer_name", "w.watchlist_name", "w.exclude_reason", "w.evidence");
        fixes.add(new Fix(
                "whitelists",
                "SELECT COUNT(*) FROM t_whitelist w WHERE " + whitelistWhere,
                """
                UPDATE t_whitelist w
                LEFT JOIN t_customer cu ON cu.id = w.customer_id
                SET w.customer_name = CASE
                      WHEN """ + anyMojibake("w.customer_name") + """
                        THEN COALESCE(cu.name, CONCAT('测试客户_', w.customer_id))
                      ELSE w.customer_name
                    END,
                    w.watchlist_name = CASE
                      WHEN """ + anyMojibake("w.watchlist_name") + """
                        THEN CONCAT('测试名单对象_', w.id)
                      ELSE w.watchlist_name
                    END,
                    w.exclude_reason = CASE
                      WHEN """ + anyMojibake("w.exclude_reason") + """
                        THEN '同名不同人白名单样本'
                      ELSE w.exclude_reason
                    END,
                    w.evidence = CASE
                      WHEN """ + anyMojibake("w.evidence") + """
                        THEN '{"evidence":"manual-review"}'
                      ELSE w.evidence
                    END
                WHERE """ + whitelistWhere));

        String txnWhere = anyMojibake("counterparty_name", "counterparty_bank", "remark");
        fixes.add(new Fix(
                "transactions",
                "SELECT COUNT(*) FROM t_transaction WHERE " + txnWhere,
                """
                UPDATE t_transaction
                SET counterparty_name = CASE
                      WHEN """ + anyMojibake("counterparty_name") + """
                        THEN CONCAT('测试交易对手_', id)
                      ELSE counterparty_name
                    END,
                    counterparty_bank = CASE
                      WHEN """ + anyMojibake("counterparty_bank") + """
                        THEN '测试银行'
                      ELSE counterparty_bank
                    END,
                    remark = CASE
                      WHEN """ + anyMojibake("remark") + """
                        THEN '测试交易样本'
                      ELSE remark
                    END
                WHERE """ + txnWhere));

        String largeReportWhere = anyMojibake("r.customer_name", "r.counterparty_info", "r.xml_content", "r.submit_response");
        fixes.add(new Fix(
                "large_txn_reports",
                "SELECT COUNT(*) FROM t_large_txn_report r WHERE " + largeReportWhere,
                """
                UPDATE t_large_txn_report r
                LEFT JOIN t_customer cu ON cu.id = r.customer_id
                SET r.customer_name = CASE
                      WHEN """ + anyMojibake("r.customer_name") + """
                        THEN COALESCE(cu.name, CONCAT('测试客户_', r.customer_id))
                      ELSE r.customer_name
                    END,
                    r.counterparty_info = CASE
                      WHEN """ + anyMojibake("r.counterparty_info") + """
                        THEN '{"counterparty":"测试交易对手"}'
                      ELSE r.counterparty_info
                    END,
                    r.xml_content = CASE
                      WHEN """ + anyMojibake("r.xml_content") + """
                        THEN '<LargeTxnReport><Mock>true</Mock></LargeTxnReport>'
                      ELSE r.xml_content
                    END,
                    r.submit_response = CASE
                      WHEN """ + anyMojibake("r.submit_response") + """
                        THEN '{"status":"ACCEPTED","mock":true}'
                      ELSE r.submit_response
                    END
                WHERE """ + largeReportWhere));

        String strWhere = anyMojibake("report_content", "analysis_opinion", "measures_taken", "reviewer_opinion", "approver_opinion", "submit_result");
        fixes.add(new Fix(
                "str_reports",
                "SELECT COUNT(*) FROM t_str_report WHERE " + strWhere,
                """
                UPDATE t_str_report
                SET report_content = CASE
                      WHEN """ + anyMojibake("report_content") + """
                        THEN CONCAT('测试可疑交易报告正文 #', id)
                      ELSE report_content
                    END,
                    analysis_opinion = CASE
                      WHEN """ + anyMojibake("analysis_opinion") + """
                        THEN '测试分析意见'
                      ELSE analysis_opinion
                    END,
                    measures_taken = CASE
                      WHEN """ + anyMojibake("measures_taken") + """
                        THEN '测试管控措施'
                      ELSE measures_taken
                    END,
                    reviewer_opinion = CASE
                      WHEN """ + anyMojibake("reviewer_opinion") + """
                        THEN '审核通过'
                      ELSE reviewer_opinion
                    END,
                    approver_opinion = CASE
                      WHEN """ + anyMojibake("approver_opinion") + """
                        THEN '签发通过'
                      ELSE approver_opinion
                    END,
                    submit_result = CASE
                      WHEN """ + anyMojibake("submit_result") + """
                        THEN 'MOCK_ACCEPTED'
                      ELSE submit_result
                    END
                WHERE """ + strWhere));

        return fixes;
    }

    private static List<Verify> verifications() {
        return List.of(
                new Verify("alerts", "SELECT COUNT(*) FROM t_alert WHERE " + anyMojibake("customer_name", "alert_summary")),
                new Verify("cases", "SELECT COUNT(*) FROM t_case WHERE " + anyMojibake("customer_name", "summary", "close_reason")),
                new Verify("screening_results", "SELECT COUNT(*) FROM t_screening_result WHERE " + anyMojibake("customer_name", "watchlist_name", "review_reason")),
                new Verify("whitelists", "SELECT COUNT(*) FROM t_whitelist WHERE " + anyMojibake("customer_name", "watchlist_name", "exclude_reason", "evidence")),
                new Verify("transactions", "SELECT COUNT(*) FROM t_transaction WHERE " + anyMojibake("counterparty_name", "counterparty_bank", "remark")),
                new Verify("large_txn_reports", "SELECT COUNT(*) FROM t_large_txn_report WHERE " + anyMojibake("customer_name", "counterparty_info", "xml_content", "submit_response")),
                new Verify("str_reports", "SELECT COUNT(*) FROM t_str_report WHERE " + anyMojibake("report_content", "analysis_opinion", "measures_taken", "reviewer_opinion", "approver_opinion", "submit_result")),
                new Verify("customers", "SELECT COUNT(*) FROM t_customer WHERE " + anyMojibake("name", "address", "residence_address", "occupation", "employer", "job_title", "remark"))
        );
    }

    private static String anyMojibake(String... columns) {
        List<String> clauses = new ArrayList<>();
        for (String column : columns) {
            List<String> columnClauses = new ArrayList<>();
            for (String marker : MOJIBAKE_MARKERS) {
                columnClauses.add(column + " COLLATE utf8mb4_bin LIKE '%" + marker + "%'");
            }
            clauses.add("(" + String.join(" OR ", columnClauses) + ")");
        }
        return "(" + String.join(" OR ", clauses) + ")";
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0;
        }
    }

    private static int update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void printRemainingSamples(Connection connection) throws SQLException {
        System.out.println();
        System.out.println("Remaining samples:");
        sample(connection, "whitelists",
                "SELECT id, customer_name, watchlist_name, exclude_reason, evidence FROM t_whitelist WHERE "
                        + anyMojibake("customer_name", "watchlist_name", "exclude_reason", "evidence") + " LIMIT 3");
        sample(connection, "large_txn_reports",
                "SELECT id, report_no, customer_name, counterparty_info, xml_content, submit_response FROM t_large_txn_report WHERE "
                        + anyMojibake("customer_name", "counterparty_info", "xml_content", "submit_response") + " LIMIT 3");
        sample(connection, "str_reports",
                "SELECT id, report_no, report_content, analysis_opinion, measures_taken, reviewer_opinion, approver_opinion, submit_result FROM t_str_report WHERE "
                        + anyMojibake("report_content", "analysis_opinion", "measures_taken", "reviewer_opinion", "approver_opinion", "submit_result") + " LIMIT 3");
    }

    private static void sample(Connection connection, String name, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            int row = 0;
            while (resultSet.next()) {
                row++;
                StringBuilder line = new StringBuilder("  ").append(name).append(" #").append(row).append(": ");
                int columns = resultSet.getMetaData().getColumnCount();
                for (int i = 1; i <= columns; i++) {
                    if (i > 1) {
                        line.append(" | ");
                    }
                    String value = resultSet.getString(i);
                    if (value != null && value.length() > 80) {
                        value = value.substring(0, 80) + "...";
                    }
                    line.append(resultSet.getMetaData().getColumnLabel(i)).append("=").append(value);
                }
                System.out.println(line);
            }
            if (row == 0) {
                System.out.println("  " + name + ": none");
            }
        }
    }
}
JAVA

"$JAVAC" -encoding UTF-8 -cp "$MYSQL_CONNECTOR_JAR" "$WORK_DIR/LegacyMockDataTool.java"

MODE="dry-run"
if [ "$EXECUTE" = true ]; then
    MODE="execute"
fi

VERIFY_ARG="no-verify"
if [ "$VERIFY" = true ]; then
    VERIFY_ARG="verify"
fi

"$JAVA" -Dfile.encoding=UTF-8 -cp "$WORK_DIR:$MYSQL_CONNECTOR_JAR" LegacyMockDataTool \
    "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$DB_PASSWORD" "$MODE" "$VERIFY_ARG"
