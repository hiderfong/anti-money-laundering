#!/bin/bash
# ============================================================================
# AML System - RBAC E2E 测试账号准备脚本
# 默认 dry-run；显式传入 --execute 才会创建/更新 e2e_* 测试账号。
# ============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-aml_system}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-aml_dev_123}"
E2E_USER_PASSWORD="${E2E_USER_PASSWORD:-Aml@Admin#2026!}"
E2E_PASSWORD_HASH="${E2E_PASSWORD_HASH:-\$2a\$10\$c4ISGZ.nKFX0iC34wYd.8.OdmgqOLJXsrmyMocQY67X4j9gjoFojq}"
EXECUTE=false

usage() {
    cat <<'EOF'
Usage:
  scripts/prepare-rbac-e2e-users.sh [--execute]

Creates or updates these local E2E users:
  e2e_admin         -> ROLE_ADMIN
  e2e_compliance    -> ROLE_COMPLIANCE
  e2e_investigator  -> ROLE_INVESTIGATOR
  e2e_viewer        -> ROLE_VIEWER

Default password:
  Aml@Admin#2026!

Options:
  --execute          实际写入数据库；不传时只输出 dry-run 检查。
  -h, --help         显示帮助。

Environment:
  DB_HOST            默认 127.0.0.1
  DB_PORT            默认 3306
  DB_NAME            默认 aml_system
  DB_USER            默认 root
  DB_PASSWORD        默认 aml_dev_123
  E2E_PASSWORD_HASH  默认 Aml@Admin#2026! 的 BCrypt hash；若改密码需同步传入 hash。
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --execute)
            EXECUTE=true
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

if ! command -v mysql >/dev/null 2>&1; then
    echo "mysql command not found. Install MySQL client or run this script from a machine with mysql CLI." >&2
    exit 1
fi

sql_escape() {
    printf "%s" "$1" | sed "s/'/''/g"
}

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

read -r -d '' CHECK_SQL <<'SQL' || true
SELECT 'required_roles' AS item, COUNT(*) AS count
FROM t_role
WHERE role_code IN ('ROLE_ADMIN', 'ROLE_COMPLIANCE', 'ROLE_INVESTIGATOR', 'ROLE_VIEWER')
  AND status = 'ENABLED';

SELECT 'existing_e2e_users' AS item, COUNT(*) AS count
FROM t_user
WHERE username IN ('e2e_admin', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer');

SELECT role_code AS missing_role
FROM (
  SELECT 'ROLE_ADMIN' AS role_code
  UNION ALL SELECT 'ROLE_COMPLIANCE'
  UNION ALL SELECT 'ROLE_INVESTIGATOR'
  UNION ALL SELECT 'ROLE_VIEWER'
) required
WHERE NOT EXISTS (
  SELECT 1 FROM t_role r WHERE r.role_code = required.role_code AND r.status = 'ENABLED'
);
SQL

read -r -d '' UPSERT_SQL <<SQL || true
START TRANSACTION;

INSERT INTO t_user
  (username, password_hash, real_name, email, phone, department, position, status, created_by, created_time)
VALUES
  ('e2e_admin',        '${PASSWORD_HASH_SQL}', 'E2E系统管理员', 'e2e_admin@test.local',        '13900001001', 'E2E测试部', '系统管理员', 'ENABLED', 'e2e', NOW()),
  ('e2e_compliance',   '${PASSWORD_HASH_SQL}', 'E2E合规专员',   'e2e_compliance@test.local',   '13900001002', 'E2E测试部', '合规专员',   'ENABLED', 'e2e', NOW()),
  ('e2e_investigator', '${PASSWORD_HASH_SQL}', 'E2E调查员',     'e2e_investigator@test.local', '13900001003', 'E2E测试部', '调查员',     'ENABLED', 'e2e', NOW()),
  ('e2e_viewer',       '${PASSWORD_HASH_SQL}', 'E2E只读用户',   'e2e_viewer@test.local',       '13900001004', 'E2E测试部', '只读用户',   'ENABLED', 'e2e', NOW())
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  real_name = VALUES(real_name),
  email = VALUES(email),
  phone = VALUES(phone),
  department = VALUES(department),
  position = VALUES(position),
  status = 'ENABLED',
  updated_by = 'e2e',
  updated_time = NOW();

DELETE ur
FROM t_user_role ur
JOIN t_user u ON u.id = ur.user_id
WHERE u.username IN ('e2e_admin', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer');

INSERT IGNORE INTO t_user_role (user_id, role_id, created_by, created_time)
SELECT u.id, r.id, 'e2e', NOW()
FROM t_user u
JOIN t_role r ON (
    (u.username = 'e2e_admin'        AND r.role_code = 'ROLE_ADMIN')
 OR (u.username = 'e2e_compliance'   AND r.role_code = 'ROLE_COMPLIANCE')
 OR (u.username = 'e2e_investigator' AND r.role_code = 'ROLE_INVESTIGATOR')
 OR (u.username = 'e2e_viewer'       AND r.role_code = 'ROLE_VIEWER')
)
WHERE u.username IN ('e2e_admin', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer')
  AND r.status = 'ENABLED';

COMMIT;

SELECT u.username, u.status, GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
FROM t_user u
LEFT JOIN t_user_role ur ON ur.user_id = u.id
LEFT JOIN t_role r ON r.id = ur.role_id
WHERE u.username IN ('e2e_admin', 'e2e_compliance', 'e2e_investigator', 'e2e_viewer')
GROUP BY u.username, u.status
ORDER BY u.username;
SQL

echo "=========================================="
echo "  AML RBAC E2E 测试账号准备"
echo "=========================================="
echo "  DB: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  Users: e2e_admin/e2e_compliance/e2e_investigator/e2e_viewer"
echo "  Password: ${E2E_USER_PASSWORD}"
echo "  Mode: $([ "$EXECUTE" = true ] && echo execute || echo dry-run)"
echo ""

if [ "$EXECUTE" = true ]; then
    printf "%s\n%s\n" "$CHECK_SQL" "$UPSERT_SQL" | mysql "${MYSQL_ARGS[@]}"
    echo ""
    echo "RBAC E2E users are ready."
else
    printf "%s\n" "$CHECK_SQL" | mysql "${MYSQL_ARGS[@]}"
    echo ""
    echo "Dry-run only. Re-run with --execute to create/update E2E users."
fi
