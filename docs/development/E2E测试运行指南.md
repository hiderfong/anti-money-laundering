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

## 8. 常用环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `BASE_URL` | `http://localhost:8080/api` | API E2E 后端地址 |
| `API_URL` | `http://localhost:8080/api` | 前端 smoke 脚本使用的 API 地址 |
| `E2E_RUN_ID` | 当前时间戳 | 本次测试数据唯一标识 |
| `E2E_PREFIX` | `E2E` | 测试数据业务前缀 |
| `E2E_IP` | `127.0.0.101/102` | 登录请求 IP，避免限流互相污染 |
| `E2E_LIMIT_IP` | `127.0.0.103` | 限流专项测试 IP |
| `DB_HOST` | `127.0.0.1` | 清理/准备脚本数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_NAME` | `aml_system` | 数据库名 |
| `DB_USER` | `root` | 数据库用户 |
| `DB_PASSWORD` | `aml_dev_123` | 数据库密码 |
