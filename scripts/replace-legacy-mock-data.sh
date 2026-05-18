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
DB_PASSWORD="${DB_PASSWORD:-CHANGE_ME_DEV_DB_PASSWORD}"
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
  DB_PASSWORD    Default CHANGE_ME_DEV_DB_PASSWORD
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
                        THEN ELT(MOD(id, 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司')
                      ELSE name
                    END,
                    address = CASE
                      WHEN """ + anyMojibake("address") + """
                        THEN '北京市朝阳区建国路88号华贸中心A座'
                      ELSE address
                    END,
                    residence_address = CASE
                      WHEN """ + anyMojibake("residence_address") + """
                        THEN '北京市朝阳区建国路88号华贸中心A座'
                      ELSE residence_address
                    END,
                    occupation = CASE
                      WHEN """ + anyMojibake("occupation") + """
                        THEN '企业管理人员'
                      ELSE occupation
                    END,
                    employer = CASE
                      WHEN """ + anyMojibake("employer") + """
                        THEN '北京云澜科技有限公司'
                      ELSE employer
                    END,
                    job_title = CASE
                      WHEN """ + anyMojibake("job_title") + """
                        THEN '财务经理'
                      ELSE job_title
                    END,
                    remark = CASE
                      WHEN """ + anyMojibake("remark") + """
                        THEN '历史演示客户资料已修正'
                      ELSE remark
                    END
                WHERE """ + customerWhere));

        String productWhere = "(" + anyMojibake("product_name", "risk_factors") + " OR product_name LIKE 'E2ERBAC产品%')";
        fixes.add(new Fix(
                "products",
                "SELECT COUNT(*) FROM t_product WHERE " + productWhere,
                """
                UPDATE t_product
                SET product_name = CASE product_code
                      WHEN 'PROD001' THEN '定期寿险A款'
                      WHEN 'PROD002' THEN '终身寿险B款'
                      WHEN 'PROD003' THEN '万能寿险C款'
                      WHEN 'PROD004' THEN '投连寿险D款'
                      WHEN 'PROD005' THEN '年金保险E款'
                      WHEN 'PROD006' THEN '重大疾病保险F款'
                      WHEN 'PROD007' THEN '医疗保险G款'
                      WHEN 'PROD008' THEN '意外伤害保险H款'
                      WHEN 'PROD009' THEN '团体保险I款'
                      WHEN 'PROD010' THEN '跨境保险J款'
                      WHEN 'PROD011' THEN '高净值客户保险K款'
                      WHEN 'PROD012' THEN '退休养老保险L款'
                      ELSE CASE
                        WHEN product_name LIKE 'E2ERBAC产品%' THEN '稳益终身寿险（权限验证版）'
                        WHEN """ + anyMojibake("product_name") + """
                          THEN ELT(MOD(id, 6) + 1, '鑫享传世终身寿险', '稳盈年金保险', '康宁重大疾病保险', '环球跨境保障计划', '尊享高净值客户保险', '团体综合保障计划')
                        ELSE product_name
                      END
                    END,
                    risk_factors = CASE
                      WHEN risk_factors IS NULL OR risk_factors = '' OR """ + anyMojibake("risk_factors") + """
                        THEN CONCAT('{"description":"', CASE product_code
                          WHEN 'PROD001' THEN '保障期限固定的人寿保险产品，适合标准客户基础保障配置。'
                          WHEN 'PROD002' THEN '终身寿险产品，具备长期保障属性，需关注保单现金价值变化。'
                          WHEN 'PROD003' THEN '万能寿险产品，兼具保障与账户价值管理特征。'
                          WHEN 'PROD004' THEN '投资连结保险产品，受市场波动影响较高，需强化适当性管理。'
                          WHEN 'PROD005' THEN '年金保险产品，适合养老和长期现金流规划。'
                          WHEN 'PROD006' THEN '重大疾病保障产品，适合健康风险保障需求。'
                          WHEN 'PROD007' THEN '医疗费用补偿类产品，交易结构相对简单。'
                          WHEN 'PROD008' THEN '意外伤害保障产品，低复杂度、低现金价值。'
                          WHEN 'PROD009' THEN '团体保险产品，需关注投保单位和被保险人批量关系。'
                          WHEN 'PROD010' THEN '跨境保障产品，涉及跨境资金和身份核验，洗钱风险较高。'
                          WHEN 'PROD011' THEN '高净值客户保险产品，需加强资金来源和受益人核验。'
                          WHEN 'PROD012' THEN '退休养老保障产品，适合长期养老资金规划。'
                          ELSE '历史演示产品资料已修正，可用于产品风险评估和业务流程测试。'
                        END, '"}')
                      ELSE risk_factors
                    END
                WHERE """ + productWhere));

        String alertWhere = anyMojibake("customer_name", "alert_summary");
        fixes.add(new Fix(
                "alerts",
                "SELECT COUNT(*) FROM t_alert WHERE " + alertWhere,
                """
                UPDATE t_alert
                SET customer_name = CASE
                      WHEN alert_no REGEXP '^ALERT[0-9]+$'
                        THEN ELT(MOD(CAST(RIGHT(alert_no, 6) AS UNSIGNED), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司')
                      ELSE ELT(MOD(id, 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司')
                    END,
                    alert_summary = CONCAT(
                      '客户交易风险预警#',
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
                        ELSE COALESCE(alert_type, '历史样本')
                      END
                    )
                WHERE """ + alertWhere));

        String caseWhere = "("
                + "c.customer_name IS NULL OR TRIM(c.customer_name) = ''"
                + " OR c.customer_name LIKE 'E2E客户!_%' ESCAPE '!'"
                + " OR c.customer_name REGEXP '^客户[0-9]+$'"
                + " OR LOWER(COALESCE(c.created_by, '')) LIKE '%e2e%'"
                + " OR c.created_by IN ('admin', 'system')"
                + " OR LOWER(COALESCE(c.updated_by, '')) LIKE '%e2e%'"
                + " OR c.updated_by IN ('admin', 'system')"
                + " OR " + anyMojibake("c.customer_name", "c.summary", "c.close_reason", "c.created_by", "c.updated_by")
                + ")";
        fixes.add(new Fix(
                "cases",
                "SELECT COUNT(*) FROM t_case c WHERE " + caseWhere,
                """
                UPDATE t_case c
                LEFT JOIN t_customer cu ON cu.id = c.customer_id
                SET c.customer_name = CASE
                      WHEN c.customer_name IS NULL OR TRIM(c.customer_name) = ''
                           OR c.customer_name LIKE 'E2E客户!_%' ESCAPE '!'
                           OR c.customer_name REGEXP '^客户[0-9]+$'
                           OR """ + anyMojibake("c.customer_name") + """
                        THEN COALESCE(cu.name, ELT(MOD(COALESCE(c.customer_id, c.id), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司'))
                      ELSE c.customer_name
                    END,
                    c.summary = CASE
                      WHEN """ + anyMojibake("c.summary") + """
                        THEN CONCAT('客户尽职调查与交易复核案件#', c.id)
                      ELSE c.summary
                    END,
                    c.close_reason = CASE
                      WHEN """ + anyMojibake("c.close_reason") + """
                        THEN '调查结论已复核，后续纳入持续监测'
                      ELSE c.close_reason
                    END,
                    c.created_by = CASE
                      WHEN LOWER(COALESCE(c.created_by, '')) LIKE '%compliance%' THEN '合规审批员'
                      WHEN LOWER(COALESCE(c.created_by, '')) LIKE '%investigator%' THEN '案件调查员'
                      WHEN LOWER(COALESCE(c.created_by, '')) LIKE '%seed%' OR LOWER(COALESCE(c.created_by, '')) LIKE '%business-seed%' THEN '合规负责人'
                      WHEN c.created_by IN ('admin', 'e2e_admin') THEN '系统管理员'
                      WHEN LOWER(COALESCE(c.created_by, '')) LIKE '%e2e%' OR LOWER(COALESCE(c.created_by, '')) LIKE '%test%' THEN '系统操作员'
                      ELSE c.created_by
                    END,
                    c.updated_by = CASE
                      WHEN LOWER(COALESCE(c.updated_by, '')) LIKE '%compliance%' THEN '合规审批员'
                      WHEN LOWER(COALESCE(c.updated_by, '')) LIKE '%investigator%' THEN '案件调查员'
                      WHEN LOWER(COALESCE(c.updated_by, '')) LIKE '%seed%' OR LOWER(COALESCE(c.updated_by, '')) LIKE '%business-seed%' THEN '合规负责人'
                      WHEN c.updated_by IN ('admin', 'e2e_admin') THEN '系统管理员'
                      WHEN LOWER(COALESCE(c.updated_by, '')) LIKE '%e2e%' OR LOWER(COALESCE(c.updated_by, '')) LIKE '%test%' THEN '系统操作员'
                      ELSE c.updated_by
                    END
                WHERE """ + caseWhere));

        String caseStatusLogWhere = "("
                + "LOWER(COALESCE(changed_by, '')) LIKE '%e2e%'"
                + " OR changed_by IN ('admin', 'system')"
                + " OR " + anyMojibake("changed_by", "remark")
                + ")";
        fixes.add(new Fix(
                "case_status_logs",
                "SELECT COUNT(*) FROM t_case_status_log WHERE " + caseStatusLogWhere,
                """
                UPDATE t_case_status_log
                SET changed_by = CASE
                      WHEN LOWER(COALESCE(changed_by, '')) LIKE '%compliance%' THEN '合规审批员'
                      WHEN LOWER(COALESCE(changed_by, '')) LIKE '%investigator%' THEN '案件调查员'
                      WHEN LOWER(COALESCE(changed_by, '')) LIKE '%seed%' OR LOWER(COALESCE(changed_by, '')) LIKE '%business-seed%' THEN '合规负责人'
                      WHEN changed_by IN ('admin', 'e2e_admin') THEN '系统管理员'
                      WHEN LOWER(COALESCE(changed_by, '')) LIKE '%e2e%' OR LOWER(COALESCE(changed_by, '')) LIKE '%test%' THEN '系统操作员'
                      ELSE changed_by
                    END,
                    remark = CASE
                      WHEN """ + anyMojibake("remark") + """
                        THEN '案件状态流转记录已修正'
                      ELSE remark
                    END
                WHERE """ + caseStatusLogWhere));

        String investigationWhere = "(customer_name LIKE 'E2E客户!_%' ESCAPE '!'"
                + " OR document_no LIKE 'E2E!_IRQ!_DOC!_%' ESCAPE '!'"
                + " OR authority_name LIKE 'E2E测试机关%'"
                + " OR summary LIKE 'E2E调查协查接口验证!_%' ESCAPE '!'"
                + " OR response_summary LIKE 'E2E调查协查已回复%'"
                + " OR " + anyMojibake("customer_name", "document_no", "authority_name", "summary", "response_summary") + ")";
        fixes.add(new Fix(
                "investigation_requests",
                "SELECT COUNT(*) FROM t_investigation_request WHERE " + investigationWhere,
                """
                UPDATE t_investigation_request
                SET customer_name = CASE
                      WHEN customer_name LIKE 'E2E客户!_%' ESCAPE '!' OR """ + anyMojibake("customer_name") + """
                        THEN ELT(MOD(COALESCE(customer_id, id), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司')
                      ELSE customer_name
                    END,
                    document_no = CASE
                      WHEN document_no LIKE 'E2E!_IRQ!_DOC!_%' ESCAPE '!' OR """ + anyMojibake("document_no") + """
                        THEN CONCAT('沪公经协查字〔2026〕', LPAD(MOD(id, 10000), 4, '0'), '号')
                      ELSE document_no
                    END,
                    authority_name = CASE
                      WHEN authority_name LIKE 'E2E测试机关%' OR """ + anyMojibake("authority_name") + """
                        THEN '上海市公安局经侦总队'
                      ELSE authority_name
                    END,
                    summary = CASE
                      WHEN summary LIKE 'E2E调查协查接口验证!_%' ESCAPE '!' OR """ + anyMojibake("summary") + """
                        THEN '有权机关调取客户尽调资料和近期交易流水，用于案件协查。'
                      ELSE summary
                    END,
                    response_summary = CASE
                      WHEN response_summary LIKE 'E2E调查协查已回复%' OR """ + anyMojibake("response_summary") + """
                        THEN '已按要求反馈客户身份资料、账户交易明细和风险复核结论。'
                      ELSE response_summary
                    END
                WHERE """ + investigationWhere));

        String rectificationWhere = "(responsible_person IN ('admin', 'system')"
                + " OR responsible_person LIKE 'e2e!_%' ESCAPE '!'"
                + " OR verified_by IN ('admin', 'system')"
                + " OR verified_by LIKE 'e2e!_%' ESCAPE '!'"
                + " OR issue_description LIKE 'E2E整改中心%'"
                + " OR issue_description LIKE 'E2E高风险客户%'"
                + " OR issue_description LIKE 'V009升级验证整改任务%'"
                + " OR completion_evidence LIKE 'E2E整改%'"
                + " OR verify_result LIKE 'E2E验证%'"
                + " OR " + anyMojibake("issue_description", "issue_category", "responsible_person", "completion_evidence", "verified_by", "verify_result") + ")";
        fixes.add(new Fix(
                "rectification_tasks",
                "SELECT COUNT(*) FROM t_rectification_task WHERE " + rectificationWhere,
                """
                UPDATE t_rectification_task
                SET issue_description = CASE
                      WHEN issue_description LIKE 'E2E整改中心%' OR issue_description LIKE 'E2E整改中心真实接口验证问题%' OR """ + anyMojibake("issue_description") + """
                        THEN '客户尽调复核流程存在缺口，需补充整改闭环材料。'
                      WHEN issue_description LIKE 'E2E高风险客户%' OR issue_description LIKE 'V009升级验证整改任务%'
                        THEN '高风险客户复核频率不足，名单筛查复核记录需补充。'
                      ELSE issue_description
                    END,
                    issue_category = CASE
                      WHEN """ + anyMojibake("issue_category") + """
                        THEN '流程缺陷'
                      ELSE issue_category
                    END,
                    responsible_person = CASE
                      WHEN responsible_person IN ('admin', 'e2e_admin') THEN '刘思远'
                      WHEN responsible_person = 'system' THEN '系统自动处理'
                      WHEN responsible_person = 'e2e_seed_operator' THEN '周明哲'
                      WHEN responsible_person = 'e2e_compliance' THEN '赵清妍'
                      WHEN responsible_person = 'e2e_investigator' THEN '陈立行'
                      WHEN responsible_person = 'e2e_viewer' THEN '李若宁'
                      WHEN responsible_person LIKE 'e2e!_%' ESCAPE '!' OR """ + anyMojibake("responsible_person") + """
                        THEN '赵清妍'
                      ELSE responsible_person
                    END,
                    completion_evidence = CASE
                      WHEN completion_evidence LIKE 'E2E整改%' OR completion_evidence LIKE 'E2E进度%' OR """ + anyMojibake("completion_evidence") + """
                        THEN '已补充客户尽调复核底稿、整改审批记录和抽样检查材料。'
                      ELSE completion_evidence
                    END,
                    verified_by = CASE
                      WHEN verified_by IN ('admin', 'e2e_admin') THEN '刘思远'
                      WHEN verified_by = 'system' THEN '系统自动处理'
                      WHEN verified_by = 'e2e_seed_operator' THEN '周明哲'
                      WHEN verified_by = 'e2e_compliance' THEN '赵清妍'
                      WHEN verified_by = 'e2e_investigator' THEN '陈立行'
                      WHEN verified_by = 'e2e_viewer' THEN '李若宁'
                      WHEN verified_by LIKE 'e2e!_%' ESCAPE '!' OR """ + anyMojibake("verified_by") + """
                        THEN '赵清妍'
                      ELSE verified_by
                    END,
                    verify_result = CASE
                      WHEN verify_result LIKE 'E2E验证%' OR """ + anyMojibake("verify_result") + """
                        THEN '整改材料完整，抽样复核通过。'
                      ELSE verify_result
                    END
                WHERE """ + rectificationWhere));

        String screeningWhere = anyMojibake("sr.customer_name", "sr.watchlist_name", "sr.review_reason");
        fixes.add(new Fix(
                "screening_results",
                "SELECT COUNT(*) FROM t_screening_result sr WHERE " + screeningWhere,
                """
                UPDATE t_screening_result sr
                LEFT JOIN t_customer cu ON cu.id = sr.customer_id
                SET sr.customer_name = CASE
                      WHEN """ + anyMojibake("sr.customer_name") + """
                        THEN COALESCE(cu.name, ELT(MOD(COALESCE(sr.customer_id, sr.id), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司'))
                      ELSE sr.customer_name
                    END,
                    sr.watchlist_name = CASE
                      WHEN """ + anyMojibake("sr.watchlist_name") + """
                        THEN 'Grace Miller'
                      ELSE sr.watchlist_name
                    END,
                    sr.review_reason = CASE
                      WHEN """ + anyMojibake("sr.review_reason") + """
                        THEN '人工复核确认为同名或证件信息不一致'
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
                        THEN COALESCE(cu.name, ELT(MOD(COALESCE(w.customer_id, w.id), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司'))
                      ELSE w.customer_name
                    END,
                    w.watchlist_name = CASE
                      WHEN """ + anyMojibake("w.watchlist_name") + """
                        THEN 'Grace Miller'
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
                        THEN ELT(MOD(id, 5) + 1, '北京恒信科技有限公司', '上海鼎源珠宝贸易有限公司', '深圳远航进出口有限公司', '广州瑞禾供应链有限公司', 'Harbor Pacific Holdings Ltd.')
                      ELSE counterparty_name
                    END,
                    counterparty_bank = CASE
                      WHEN """ + anyMojibake("counterparty_bank") + """
                        THEN ELT(MOD(id, 4) + 1, '招商银行北京分行', '中国工商银行上海分行', '中国银行深圳分行', 'HSBC Hong Kong')
                      ELSE counterparty_bank
                    END,
                    remark = CASE
                      WHEN """ + anyMojibake("remark") + """
                        THEN '保费、退保或客户资金往来交易记录'
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
                        THEN COALESCE(cu.name, ELT(MOD(COALESCE(r.customer_id, r.id), 6) + 1, '张晨曦', '李若宁', '周建国', '王嘉宁', '上海华颐供应链管理有限公司', '深圳远航进出口有限公司'))
                      ELSE r.customer_name
                    END,
                    r.counterparty_info = CASE
                      WHEN """ + anyMojibake("r.counterparty_info") + """
                        THEN '{"counterparty":"上海鼎源珠宝贸易有限公司","bank":"中国工商银行上海分行"}'
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

        String strWhere = "("
                + "r.report_content IS NULL OR TRIM(r.report_content) = ''"
                + " OR r.analysis_opinion IS NULL OR TRIM(r.analysis_opinion) = ''"
                + " OR r.measures_taken IS NULL OR TRIM(r.measures_taken) = ''"
                + " OR " + anyMojibake("r.report_content", "r.analysis_opinion", "r.measures_taken", "r.reviewer_opinion", "r.approver_opinion", "r.submit_result")
                + ")";
        fixes.add(new Fix(
                "str_reports",
                "SELECT COUNT(*) FROM t_str_report r WHERE " + strWhere,
                """
                UPDATE t_str_report r
                LEFT JOIN t_case c ON c.id = r.case_id
                LEFT JOIN t_customer cu ON cu.id = r.customer_id
                SET r.report_content = CASE
                      WHEN r.report_content IS NULL OR TRIM(r.report_content) = '' OR """ + anyMojibake("r.report_content") + """
                        THEN CONCAT(
                          '可疑交易报告：客户 ',
                          COALESCE(cu.name, c.customer_name, CONCAT('客户ID ', r.customer_id), '相关客户'),
                          ' 涉及',
                          COALESCE(NULLIF(c.summary, ''), '异常交易行为、身份背景或资金来源说明不足等可疑线索'),
                          '。需结合客户尽职调查资料、交易流水、名单筛查和人工复核结论进行持续监测并按要求报送。'
                        )
                      ELSE r.report_content
                    END,
                    r.analysis_opinion = CASE
                      WHEN r.analysis_opinion IS NULL OR TRIM(r.analysis_opinion) = '' OR """ + anyMojibake("r.analysis_opinion") + """
                        THEN '客户身份、交易目的和资金来源存在疑点，建议按可疑交易持续跟踪。'
                      ELSE r.analysis_opinion
                    END,
                    r.measures_taken = CASE
                      WHEN r.measures_taken IS NULL OR TRIM(r.measures_taken) = '' OR """ + anyMojibake("r.measures_taken") + """
                        THEN '已加强客户尽职调查，限制高风险交易并补充资金来源材料。'
                      ELSE r.measures_taken
                    END,
                    r.reviewer_opinion = CASE
                      WHEN """ + anyMojibake("r.reviewer_opinion") + """
                        THEN '审核通过'
                      ELSE r.reviewer_opinion
                    END,
                    r.approver_opinion = CASE
                      WHEN """ + anyMojibake("r.approver_opinion") + """
                        THEN '签发通过'
                      ELSE r.approver_opinion
                    END,
                    r.submit_result = CASE
                      WHEN """ + anyMojibake("r.submit_result") + """
                        THEN 'MOCK_ACCEPTED'
                      ELSE r.submit_result
                    END
                WHERE """ + strWhere));

        return fixes;
    }

    private static List<Verify> verifications() {
        return List.of(
                new Verify("alerts", "SELECT COUNT(*) FROM t_alert WHERE " + anyMojibake("customer_name", "alert_summary")),
                new Verify("cases", "SELECT COUNT(*) FROM t_case WHERE customer_name IS NULL OR TRIM(customer_name) = '' OR customer_name LIKE 'E2E客户!_%' ESCAPE '!' OR customer_name REGEXP '^客户[0-9]+$' OR LOWER(COALESCE(created_by, '')) LIKE '%e2e%' OR created_by IN ('admin', 'system') OR LOWER(COALESCE(updated_by, '')) LIKE '%e2e%' OR updated_by IN ('admin', 'system') OR " + anyMojibake("customer_name", "summary", "close_reason", "created_by", "updated_by")),
                new Verify("case_status_logs", "SELECT COUNT(*) FROM t_case_status_log WHERE LOWER(COALESCE(changed_by, '')) LIKE '%e2e%' OR changed_by IN ('admin', 'system') OR " + anyMojibake("changed_by", "remark")),
                new Verify("investigation_requests", "SELECT COUNT(*) FROM t_investigation_request WHERE customer_name LIKE 'E2E客户!_%' ESCAPE '!' OR document_no LIKE 'E2E!_IRQ!_DOC!_%' ESCAPE '!' OR authority_name LIKE 'E2E测试机关%' OR summary LIKE 'E2E调查协查接口验证!_%' ESCAPE '!' OR response_summary LIKE 'E2E调查协查已回复%' OR " + anyMojibake("customer_name", "document_no", "authority_name", "summary", "response_summary")),
                new Verify("rectification_tasks", "SELECT COUNT(*) FROM t_rectification_task WHERE responsible_person IN ('admin', 'system') OR responsible_person LIKE 'e2e!_%' ESCAPE '!' OR verified_by IN ('admin', 'system') OR verified_by LIKE 'e2e!_%' ESCAPE '!' OR issue_description LIKE 'E2E整改中心%' OR issue_description LIKE 'E2E高风险客户%' OR issue_description LIKE 'V009升级验证整改任务%' OR completion_evidence LIKE 'E2E整改%' OR verify_result LIKE 'E2E验证%' OR " + anyMojibake("issue_description", "issue_category", "responsible_person", "completion_evidence", "verified_by", "verify_result")),
                new Verify("screening_results", "SELECT COUNT(*) FROM t_screening_result WHERE " + anyMojibake("customer_name", "watchlist_name", "review_reason")),
                new Verify("whitelists", "SELECT COUNT(*) FROM t_whitelist WHERE " + anyMojibake("customer_name", "watchlist_name", "exclude_reason", "evidence")),
                new Verify("transactions", "SELECT COUNT(*) FROM t_transaction WHERE " + anyMojibake("counterparty_name", "counterparty_bank", "remark")),
                new Verify("large_txn_reports", "SELECT COUNT(*) FROM t_large_txn_report WHERE " + anyMojibake("customer_name", "counterparty_info", "xml_content", "submit_response")),
                new Verify("str_reports", "SELECT COUNT(*) FROM t_str_report WHERE report_content IS NULL OR TRIM(report_content) = '' OR analysis_opinion IS NULL OR TRIM(analysis_opinion) = '' OR measures_taken IS NULL OR TRIM(measures_taken) = '' OR " + anyMojibake("report_content", "analysis_opinion", "measures_taken", "reviewer_opinion", "approver_opinion", "submit_result")),
                new Verify("products", "SELECT COUNT(*) FROM t_product WHERE " + anyMojibake("product_name", "risk_factors")),
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
        sample(connection, "products",
                "SELECT id, product_code, product_name, risk_factors FROM t_product WHERE "
                        + anyMojibake("product_name", "risk_factors") + " LIMIT 3");
        sample(connection, "cases",
                "SELECT id, case_no, customer_name, created_by, updated_by, summary FROM t_case WHERE customer_name IS NULL OR TRIM(customer_name) = '' OR customer_name LIKE 'E2E客户!_%' ESCAPE '!' OR customer_name REGEXP '^客户[0-9]+$' OR LOWER(COALESCE(created_by, '')) LIKE '%e2e%' OR created_by IN ('admin', 'system') OR LOWER(COALESCE(updated_by, '')) LIKE '%e2e%' OR updated_by IN ('admin', 'system') OR "
                        + anyMojibake("customer_name", "summary", "close_reason", "created_by", "updated_by") + " LIMIT 3");
        sample(connection, "case_status_logs",
                "SELECT id, case_id, changed_by, remark FROM t_case_status_log WHERE LOWER(COALESCE(changed_by, '')) LIKE '%e2e%' OR changed_by IN ('admin', 'system') OR "
                        + anyMojibake("changed_by", "remark") + " LIMIT 3");
        sample(connection, "investigation_requests",
                "SELECT id, request_no, document_no, customer_name, authority_name, summary, response_summary FROM t_investigation_request WHERE customer_name LIKE 'E2E客户!_%' ESCAPE '!' OR document_no LIKE 'E2E!_IRQ!_DOC!_%' ESCAPE '!' OR authority_name LIKE 'E2E测试机关%' OR summary LIKE 'E2E调查协查接口验证!_%' ESCAPE '!' OR response_summary LIKE 'E2E调查协查已回复%' OR "
                        + anyMojibake("customer_name", "document_no", "authority_name", "summary", "response_summary") + " LIMIT 3");
        sample(connection, "rectification_tasks",
                "SELECT id, issue_description, responsible_person, completion_evidence, verified_by, verify_result FROM t_rectification_task WHERE responsible_person IN ('admin', 'system') OR responsible_person LIKE 'e2e!_%' ESCAPE '!' OR verified_by IN ('admin', 'system') OR verified_by LIKE 'e2e!_%' ESCAPE '!' OR issue_description LIKE 'E2E整改中心%' OR issue_description LIKE 'E2E高风险客户%' OR issue_description LIKE 'V009升级验证整改任务%' OR completion_evidence LIKE 'E2E整改%' OR verify_result LIKE 'E2E验证%' OR "
                        + anyMojibake("issue_description", "issue_category", "responsible_person", "completion_evidence", "verified_by", "verify_result") + " LIMIT 3");
        sample(connection, "large_txn_reports",
                "SELECT id, report_no, customer_name, counterparty_info, xml_content, submit_response FROM t_large_txn_report WHERE "
                        + anyMojibake("customer_name", "counterparty_info", "xml_content", "submit_response") + " LIMIT 3");
        sample(connection, "str_reports",
                "SELECT id, report_no, report_content, analysis_opinion, measures_taken, reviewer_opinion, approver_opinion, submit_result FROM t_str_report WHERE report_content IS NULL OR TRIM(report_content) = '' OR analysis_opinion IS NULL OR TRIM(analysis_opinion) = '' OR measures_taken IS NULL OR TRIM(measures_taken) = '' OR "
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
