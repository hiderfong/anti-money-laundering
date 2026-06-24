# E2E 测试运行指南

> 版本：v1.0  
> 日期：2026-05-10

---

## 1. 启动服务

后端：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,no-es \
  -Dspring-boot.run.arguments="--aml.kafka.enabled=false"
```

本地未启动 Kafka 时，建议保持 `--aml.kafka.enabled=false`，避免 Kafka topic 初始化和消费者连接影响验收日志。若本机 MySQL `root` 为空密码，可追加：

```bash
-Dspring-boot.run.arguments="--spring.datasource.password= --aml.kafka.enabled=false"
```

如本地未启动 Redis，可使用 `no-redis` profile。该模式会禁用 Redis/Redisson 自动配置，并使用内存令牌存储与内存限流，适合本地 E2E 验收：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,no-redis \
  -Dspring-boot.run.arguments="--spring.datasource.password= --aml.kafka.enabled=false"
```

前端：

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

---

## 2. 准备 RBAC 测试账号

先 dry-run 检查：

```bash
scripts/prepare-rbac-e2e-users.sh
```

确认无误后写入本地测试库：

```bash
scripts/prepare-rbac-e2e-users.sh --execute
```

默认会准备以下账号，密码均为 `admin123`：

| 用户名 | 角色 |
|--------|------|
| `e2e_admin` | `ROLE_ADMIN` |
| `e2e_compliance` | `ROLE_COMPLIANCE` |
| `e2e_investigator` | `ROLE_INVESTIGATOR` |
| `e2e_viewer` | `ROLE_VIEWER` |

---

## 3. 运行 API E2E

```bash
BASE_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/e2e-test.sh
```

脚本会自动生成唯一测试数据：

- 客户名：`E2E客户_${E2E_RUN_ID}`
- 交易流水：`E2E_TXN_${E2E_RUN_ID}_001`
- 邮箱：`e2e_${E2E_RUN_ID}@test.local`

## 3.1 准备业务闭环种子数据

完整业务流、页面验收或外部 Agent 测试前，建议额外写入测试专用业务数据。该数据覆盖客户、名单筛查、交易监测、预警、案件、报送、自评估和整改通知，并可被清理脚本统一回收。

先 dry-run 预览：

```bash
export E2E_RUN_ID=$(date +%Y%m%d%H%M%S)
bash scripts/seed-e2e-business-data.sh --run-id "$E2E_RUN_ID"
```

确认无误后写入测试库：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=CHANGE_ME_DEV_DB_PASSWORD \
bash scripts/seed-e2e-business-data.sh --execute --run-id "$E2E_RUN_ID"
```

写入后验证：

```bash
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=aml_system \
DB_USER=root \
DB_PASSWORD=CHANGE_ME_DEV_DB_PASSWORD \
bash scripts/seed-e2e-business-data.sh --verify --run-id "$E2E_RUN_ID"
```

无法在沙箱内连接 MySQL 时，可使用 `--sql-only` 导出 SQL 后交给外部 Agent 执行。详细步骤见 [E2E业务数据种子脚本使用指南](./E2E业务数据种子脚本使用指南.md)。

---

## 4. 运行前端 smoke E2E

```bash
BASE_URL=http://127.0.0.1:5173 \
API_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/frontend-e2e.sh
```

该脚本覆盖：

- 前端首页与登录页可访问。
- Vite `/api` 代理可用。
- 登录 API 与 Token 刷新。
- 核心 SPA 路由返回 200。
- 核心后端模块 API 返回 200。
- 登录限流返回 429。
- 前端 dist 构建产物存在。

---

## 5. 运行前端浏览器 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/frontend-browser-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:browser
```

该脚本覆盖：

- 使用真实浏览器登录 `admin/admin123`，并进入 `/dashboard`。
- 刷新页面后仍保持登录态。
- `/api/auth/me` 返回 `roles`、`permissions`，且管理员包含 `ROLE_ADMIN` 与 `system:user`。
- `/dashboard`、`/system`、`/kyc`、`/monitoring`、`/alerts`、`/products` 核心页面渲染非空。
- 页面无 Vite/Vue 框架错误覆盖层。
- 页面访问过程中无新增 `console.error`、`console.warn` 或 `pageerror`。

可选环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `FRONTEND_URL` | `http://127.0.0.1:5173` | 前端地址 |
| `E2E_USERNAME` | `admin` | 登录用户名 |
| `E2E_PASSWORD` | `admin123` | 登录密码 |
| `HEADLESS` | `true` | 设置为 `false` 可显示浏览器 |
| `PLAYWRIGHT_CHANNEL` | `chrome` | 浏览器 channel，常用 `chrome`/`msedge` |
| `PLAYWRIGHT_EXECUTABLE_PATH` | 空 | 指定浏览器可执行文件 |
| `SCREENSHOT_DIR` | `/tmp/aml-frontend-browser-e2e` | 截图输出目录 |

若本机没有可用 Chrome/Edge，可在 `frontend` 目录运行：

```bash
npx playwright install chromium
```

---

## 5.1 运行全量人工仿真浏览器 E2E

该脚本适合在本机、局域网测试服务器或外部 Agent 环境执行。它会使用真实浏览器登录、逐页巡检核心菜单、截图留痕，并核对客户、交易、预警、案件、报送、自评估、整改、模型、法规资料库、AI 风险复核池、交易图谱和预警处置链路等关键数据。

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_USERNAME=admin \
E2E_PASSWORD=<测试密码> \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/full-human-like-browser-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_USERNAME=admin \
E2E_PASSWORD=<测试密码> \
npm run e2e:human-like
```

默认输出：

- Markdown 报告：`/tmp/aml-human-like-browser-e2e/<E2E_RUN_ID>/human-like-browser-e2e-report.md`
- 页面截图：`/tmp/aml-human-like-browser-e2e/<E2E_RUN_ID>/screenshots`

可选环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `HUMAN_E2E_ARTIFACT_DIR` | `/tmp/aml-human-like-browser-e2e` | 报告和截图根目录 |
| `HUMAN_E2E_REPORT` | 自动生成 | 指定 Markdown 报告路径 |
| `HUMAN_E2E_GRAPH_CUSTOMER_ID` | `18` | 用于客户关系图谱和交易图谱核对的客户 ID |
| `HUMAN_E2E_ALERT_ID` | `17` | 用于预警处置链路核对的预警 ID |
| `HUMAN_E2E_DENSITY_THRESHOLD` | `4` | 交易异常网络密度检测阈值 |
| `HUMAN_E2E_SYNC_GRAPH` | `false` | 设置为 `true` 时会先调用图数据库同步接口 |
| `HEADLESS` | `true` | 设置为 `false` 可显示浏览器，便于观察人工仿真动作 |

建议在补充测试数据后运行；如果目标环境客户 ID 或预警 ID 不同，通过 `HUMAN_E2E_GRAPH_CUSTOMER_ID` 和 `HUMAN_E2E_ALERT_ID` 指定对应记录。

---

## 5.2 运行客户管理真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/customer-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:customer
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号并进入 `/kyc`。
- 通过页面“新增客户”弹窗创建唯一测试客户。
- 使用客户名称搜索列表，并进入客户详情页。
- 验证客户详情展示客户画像和画像雷达图。
- 验证客户关系图谱 API 返回客户节点，并检查“关系图谱”标签页可见、可刷新。
- 输出客户列表、客户详情、客户关系图谱截图到 `SCREENSHOT_DIR`。

---

## 5.3 运行名单筛查真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/screening-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:screening
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户，避免依赖固定演示数据。
- 进入 `/screening`，使用页面表单按客户 ID 触发单笔名单筛查。
- 打开“批量筛查”弹窗，提交客户 ID 批量筛查。
- 进入“白名单管理”，新增白名单记录并验证列表展示。
- 输出单笔筛查、白名单管理截图到 `SCREENSHOT_DIR`。

---

## 5.4 运行交易监测真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/monitoring-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:monitoring
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户和大额现金交易，避免依赖固定演示数据。
- 进入 `/monitoring`，按交易编号搜索交易列表。
- 打开交易详情，验证“资金流向图”渲染。
- 从交易行点击“图谱”，验证交易关系图谱和统计区更新。
- 输出交易列表、交易详情、交易关系图谱截图到 `SCREENSHOT_DIR`。

---

## 5.5 运行预警管理真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/alert-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:alert
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户、关联交易和人工预警，避免依赖固定演示数据。
- 进入 `/alerts`，验证预警列表展示本次创建的预警。
- 打开预警详情，验证预警处置链路和关联交易证据展示。
- 通过页面完成预警指派和确认可疑处理，并验证状态更新为“已确认”。
- 输出预警列表、预警详情、处理后列表截图到 `SCREENSHOT_DIR`。

---

## 5.6 运行案件管理真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/case-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:case
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户、人工预警，并确认预警自动生成案件。
- 进入 `/cases`，验证案件列表展示本次创建的案件。
- 打开案件详情，验证案件处置时间轴。
- 通过页面新增调查记录，并依次执行开始调查、提交审批、审批通过、关闭案件。
- 复查案件详情，验证调查记录和关闭节点已沉淀。
- 输出案件列表、案件详情、关闭后列表截图到 `SCREENSHOT_DIR`。

---

## 5.7 运行 STR 报告真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/str-report-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:str-report
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户、人工预警和调查中案件，避免依赖固定演示数据。
- 进入 `/str-reports`，通过页面新建 STR 可疑交易报告。
- 通过页面提交审核、审核通过，并报送监管。
- 复查 STR 报告详情，验证报告内容、审核意见和最终报送状态。
- 输出 STR 报告列表、报送后列表截图到 `SCREENSHOT_DIR`。

---

## 5.8 运行大额交易报告真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/large-report-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:large-report
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过 API 准备唯一测试客户和大额现金交易，避免依赖固定演示数据。
- 进入 `/reporting`，通过页面按交易 ID 生成大额交易报告。
- 打开报告详情，验证客户、交易金额和状态展示。
- 通过页面完成审核，并提交监管报送。
- 复查报送后详情，验证最终状态为“已报送”。
- 输出大额报告列表、报告详情、报送后列表截图到 `SCREENSHOT_DIR`。

---

## 5.9 运行产品管理真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/product-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:product
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过页面新建唯一产品，覆盖产品编码、名称、类型、风险等级和描述。
- 按产品编码查询，验证后端筛选条件和列表展示一致。
- 打开产品详情，验证产品简介展示。
- 通过页面编辑产品名称和描述。
- 通过页面发起产品风险评估，验证评估接口返回风险评分和风险等级。
- 复查产品详情中的评估历史，验证评估人、评分和评估因素展示。
- 输出产品创建、详情、评估后列表截图到 `SCREENSHOT_DIR`。

---

## 5.10 运行风险自评估真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/assessment-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:assessment
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 进入 `/assessment`，验证自评估风险画像和整改闭环图表区域可渲染。
- 通过页面创建年度风险自评估任务。
- 查询启用评估指标，并通过页面逐项提交固有风险、控制有效性评分。
- 通过页面完成评估并审批通过，验证综合评分和风险等级回显。
- 打开评估详情，验证评分明细和评分依据展示。
- 在自评估页面的整改任务页签创建关联整改任务，验证任务进入“待整改”状态。
- 输出自评估创建、审批后列表、关联整改任务截图到 `SCREENSHOT_DIR`。

---

## 5.11 运行整改中心真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/rectification-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:rectification
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 进入 `/rectifications`，验证整改闭环状态图和问题来源/严重程度图表区域可渲染。
- 通过页面新增监管检查来源整改任务。
- 按责任人筛选，验证新建任务可被列表查询命中。
- 通过页面更新整改进度为 100%，并将状态推进到“已完成”。
- 通过页面提交整改验证并置为“已验证”。
- 按“已验证”状态复查闭环任务。
- 输出整改任务创建和验证后列表截图到 `SCREENSHOT_DIR`。

---

## 5.12 运行通知中心案件通知真实 UI 闭环 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/notification-ui-e2e.sh
```

也可以在前端目录内直接运行：

```bash
cd frontend
FRONTEND_URL=http://127.0.0.1:5173 npm run e2e:notification
```

该脚本覆盖：

- 使用真实浏览器登录管理员账号。
- 通过接口准备客户、手工预警、预警分派和预警处置数据，触发案件创建。
- 验证案件创建后会生成 `CASE` 类型通知，且通知 `relatedId` 对应真实案件 ID。
- 进入 `/notifications`，按案件通知筛选并打开通知详情。
- 验证通知详情展示案件编号、客户名称和案件摘要，并将通知标记为已读。
- 通过通知详情跳转案件管理，验证 `/cases?caseId=...` 能自动打开对应案件详情。
- 输出通知详情和案件详情截图到 `SCREENSHOT_DIR`。

---

## 6. 运行 RBAC E2E

```bash
API_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$(date +%Y%m%d%H%M%S) \
bash scripts/rbac-e2e.sh
```

该脚本覆盖：

- `admin`、`e2e_compliance`、`e2e_investigator`、`e2e_viewer` 登录。
- `/auth/me` 返回角色和权限。
- 只读用户、调查员、合规专员调用越权写接口返回 403。
- 调查员创建客户、合规专员创建产品、管理员创建系统用户等允许路径返回 200。
- API 新建用户可立即登录，验证默认状态为 `ENABLED`。

---

## 7. 清理 E2E 测试数据

先 dry-run 查看将清理的行数：

```bash
scripts/cleanup-e2e-data.sh
```

确认后执行删除：

```bash
scripts/cleanup-e2e-data.sh --execute
```

如需同时清理早期固定客户名 `E2E测试客户`：

```bash
scripts/cleanup-e2e-data.sh --include-legacy --execute
```

清理脚本默认只识别：

- 名称或编号以 `E2E` 开头的客户、交易、预警、案件、报告、产品。
- 用户名以 `e2e_` 开头的测试用户。
- 与上述测试数据关联的日志、明细、汇总表。

---

## 8. CI E2E 报告归档

CI 中 E2E 步骤会通过 `scripts/ci-e2e-step.sh` 包装执行，并由 `scripts/collect-e2e-artifacts.sh` 汇总报告。

默认归档目录：

```bash
/tmp/aml-e2e-artifacts
```

归档内容：

| 文件/目录 | 说明 |
|-----------|------|
| `e2e-summary.md` | 本次 E2E 汇总报告，包含 run id、commit、各步骤结果 |
| `manifest.txt` | artifact 文件清单 |
| `logs/*.log` | 各 E2E 脚本独立日志、后端日志、前端日志 |
| `status/*.env` | 每个 E2E 步骤的退出码和起止时间 |
| `screenshots/*.png` | Playwright 浏览器 E2E 截图 |
| `health.json` | 后端健康检查响应 |

GitHub Actions 会上传 artifact：

```text
e2e-release-gate-artifacts
```

本地手动收集：

```bash
E2E_ARTIFACT_DIR=/tmp/aml-e2e-artifacts \
bash scripts/collect-e2e-artifacts.sh
```

---

## 9. 常用环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080/api` | API E2E 后端地址 |
| `API_URL` | `http://localhost:8080/api` | 前端 smoke 脚本使用的 API 地址 |
| `E2E_RUN_ID` | 当前时间戳 | 本次测试数据唯一标识 |
| `E2E_PREFIX` | `E2E` | 测试数据业务前缀 |
| `E2E_ARTIFACT_DIR` | `/tmp/aml-e2e-artifacts` | CI E2E 归档目录 |
| `SCREENSHOT_DIR` | `/tmp/aml-frontend-browser-e2e` | 浏览器 E2E 截图目录 |
| `E2E_IP` | `127.0.0.101/102` | 登录请求 IP，避免限流互相污染 |
| `E2E_LIMIT_IP` | `127.0.0.103` | 限流专项测试 IP |
| `DB_HOST` | `127.0.0.1` | 清理/准备脚本数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `aml_system` | 数据库名 |
| `DB_USER` | `root` | 数据库用户 |
| `DB_PASSWORD` | `CHANGE_ME_DEV_DB_PASSWORD` | 数据库密码 |
