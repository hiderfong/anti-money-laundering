# E2E 业务数据种子脚本使用指南

> 日期：2026-05-11
> 适用范围：本地、CI、准生产演练等非生产环境。禁止直接对生产库执行。

---

## 1. 目标

`scripts/seed-e2e-business-data.sh` 用于生成一组可识别、可重复验证、可清理的反洗钱业务闭环测试数据，补足基础迁移脚本只包含字典、用户、角色、权限、参数的问题。

这组数据覆盖：

- 登录与多角色用户：`e2e_seed_operator`、`e2e_compliance`、`e2e_investigator`、`e2e_viewer`。
- 客户：普通个人、制裁命中个人、PEP 个人、法人客户和受益所有人。
- 名单筛查：名单源、名单条目、别名、证件、筛查请求、筛查结果、白名单。
- 交易监测：产品、产品风险评估、保单、普通交易、大额现金交易、跨境退保交易、日汇总、规则和执行日志。
- 预警案件：大额预警、名单命中预警、分配日志、案件、状态日志、调查记录。
- 报送：STR 报告、大额交易报告、报送日志。
- 生产化验收辅助：自评估、指标、评分、整改、通知、审计日志。

---

## 2. 前置条件

确认数据库已完成 Flyway 迁移，至少包含：

- `V001__init_schema.sql`
- `V002__init_data.sql`
- `V003__rbac_roles_permissions.sql`
- `V004__fix_admin_password_hash.sql`

确认 MySQL CLI 可用：

```bash
mysql --version
```

确认能连接测试库：

```bash
MYSQL_PWD=aml_dev_123 mysql -h 127.0.0.1 -P 3306 -uroot aml_system -e "SELECT COUNT(*) FROM t_user;"
```

---

## 3. 推荐执行方式

先固定本轮 run id：

```bash
export E2E_RUN_ID=$(date +%Y%m%d%H%M%S)
```

预览将生成的数据，不写库：

```bash
bash scripts/seed-e2e-business-data.sh --run-id "$E2E_RUN_ID"
```

写入测试数据：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=aml_dev_123 \
bash scripts/seed-e2e-business-data.sh --execute --run-id "$E2E_RUN_ID"
```

验证写入结果：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=aml_dev_123 \
bash scripts/seed-e2e-business-data.sh --verify --run-id "$E2E_RUN_ID"
```

也可以显式使用一次性写入并验证模式：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=aml_dev_123 \
bash scripts/seed-e2e-business-data.sh --execute --verify --run-id "$E2E_RUN_ID"
```

注意：`--verify` 统计的是当前 `--run-id` 对应的数据，不是所有 `E2E` 前缀历史数据。

---

## 4. 沙箱外 Agent 执行方式

如果 Agent 不能直接运行脚本，但能执行 SQL，可导出 SQL：

```bash
export E2E_RUN_ID=$(date +%Y%m%d%H%M%S)
bash scripts/seed-e2e-business-data.sh --sql-only --run-id "$E2E_RUN_ID" > /tmp/aml-e2e-business-seed.sql
```

交给外部 Agent 执行：

```bash
MYSQL_PWD=aml_dev_123 \
mysql -h 127.0.0.1 -P 3306 -uroot aml_system < /tmp/aml-e2e-business-seed.sql
```

执行后仍用脚本验证：

```bash
bash scripts/seed-e2e-business-data.sh --verify --run-id "$E2E_RUN_ID"
```

---

## 5. 预期验证结果

`--verify` 至少应看到以下数量：

| artifact | 期望行数 |
| --- | ---: |
| users | 4 |
| customers | 4 |
| watchlist_sources | 1 |
| watchlists | 1 |
| screening_requests | 1 |
| screening_results | 1 |
| products | 1 |
| policies | 1 |
| transactions | 3 |
| rules | 2 |
| alerts | 2 |
| cases | 1 |
| str_reports | 1 |
| large_txn_reports | 1 |
| self_assessments | 1 |
| rectification_tasks | 1 |
| notifications | 1 |
| audit_logs | 1 |

如果某项为 0，优先检查：

- 是否传入了同一个 `E2E_RUN_ID`。
- 当前库是否完成所有 migration。
- `V003` 是否已包含 `ROLE_COMPLIANCE`、`ROLE_INVESTIGATOR`、`ROLE_VIEWER`。
- SQL 是否执行到 `COMMIT`。

---

## 6. 页面和 API 验证建议

启动后端和前端后，可用管理员登录：

```text
用户名：admin
密码：Aml@Admin#2026!
```

重点检查页面：

- `/dashboard`：客户、交易、预警、案件、报告统计不为空。
- `/kyc`：可搜索 `E2E普通客户`、`E2E制裁命中客户`、`E2E法人客户`。
- `/screening`：可看到 `CONFIRMED` 的名单命中样本。
- `/monitoring`：可看到 `E2ETX...` 的 3 笔交易。
- `/alerts`：可看到大额现金和名单命中两类预警。
- `/cases`：可看到已提交的可疑交易案件。
- `/reporting`：可看到大额交易报告。
- `/str-reports`：可看到 STR 报告。
- `/products`：可看到高风险测试产品。

常用 API 快速验证：

```bash
TOKEN=$(curl -sS -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Aml@Admin#2026!"}' | jq -r '.data.accessToken')

curl -sS "http://127.0.0.1:8080/api/kyc/customers/page?page=1&size=10&keyword=E2E" \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -sS "http://127.0.0.1:8080/api/alerts/page?page=1&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -sS "http://127.0.0.1:8080/api/cases/page?page=1&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -sS "http://127.0.0.1:8080/api/dashboard/overview" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## 7. 清理

先 dry-run 查看将清理的数据：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=aml_dev_123 \
bash scripts/cleanup-e2e-data.sh --prefix E2E
```

确认后清理：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=aml_dev_123 \
bash scripts/cleanup-e2e-data.sh --prefix E2E --execute
```

注意：清理脚本会删除用户名以 `e2e_` 开头的测试用户。如后续还要跑 RBAC E2E，请重新执行：

```bash
bash scripts/prepare-rbac-e2e-users.sh --execute
```

---

## 8. 外部 Agent 回传建议

外部 Agent 执行后建议回传：

- `seed-e2e-business-data.sh --execute` 完整输出。
- `seed-e2e-business-data.sh --verify` 输出。
- 关键页面截图：`/dashboard`、`/kyc`、`/screening`、`/monitoring`、`/alerts`、`/cases`、`/reporting`、`/str-reports`。
- 若失败，回传 MySQL 错误行、当前 `E2E_RUN_ID`、数据库版本和最近一次 Flyway migration 状态。
