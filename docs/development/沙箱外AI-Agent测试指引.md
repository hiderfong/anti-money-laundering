# 沙箱外 AI Agent 测试指引

> 日期：2026-05-10  
> 适用场景：在沙箱外启动 AI Agent 或本机终端，对 AML 系统执行真实服务启动、API E2E、前端 smoke、RBAC 权限验收。

---

## 1. 测试目标

本轮测试需要确认：

- 后端可在本地启动，并绕开本机未安装 Redis/Kafka/ES/Neo4j 时的阻塞。
- 登录接口返回 `roles` 和 `permissions`。
- 核心 API E2E 全通过。
- 前端页面、Vite 代理、登录、Token 刷新和核心路由可用。
- RBAC 四角色真实账号登录可用，越权写接口返回 `403`。
- API 创建的新用户默认状态为 `ENABLED`，可立即登录。

---

## 2. 前置条件

在仓库根目录执行：

```bash
cd /Users/nathan/Work/Anti-money-Laundering
```

确认本机具备：

```bash
java -version
mvn -version
node -v
npm -v
mysql --version
jq --version
curl --version
```

要求：

- Java 21。
- Maven 可用。
- Node/npm 可用。
- MySQL 可用，默认数据库名 `aml_system`。
- `jq` 建议安装，E2E 脚本会用它解析 JSON。
- Redis、Kafka、Elasticsearch、Neo4j 均可不启动，按本文的 `no-redis` 本地模式运行。

---

## 3. 推荐测试模式

推荐使用最小本地依赖模式：

- MySQL：启用。
- Redis：禁用，使用内存令牌存储和内存限流。
- Kafka：禁用。
- Elasticsearch/Neo4j：禁用。

后端启动 profile 使用：

```text
dev,no-redis
```

`no-redis` profile 已排除 Redis/Redisson/ES/Neo4j 自动配置；Kafka 需通过启动参数显式关闭：

```text
--aml.kafka.enabled=false
```

---

## 4. 数据库准备

如果本机 MySQL root 为空密码：

```bash
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS aml_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

如果 root 密码为默认开发密码：

```bash
mysql -uroot -paml_dev_123 -e "CREATE DATABASE IF NOT EXISTS aml_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

后端启动时会通过 Flyway 自动执行迁移。

---

## 5. 启动后端

### 5.1 root 空密码

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,no-redis \
  -Dspring-boot.run.arguments="--spring.datasource.password= --aml.kafka.enabled=false"
```

### 5.2 root 使用默认开发密码

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,no-redis \
  -Dspring-boot.run.arguments="--spring.datasource.password=aml_dev_123 --aml.kafka.enabled=false"
```

后端成功标志：

- 日志出现 `Tomcat started on port 8080`。
- 未持续出现 Kafka `localhost:9092` 连接失败刷屏。
- 未出现 Redisson/Redis 连接失败导致启动退出。

健康检查：

```bash
curl -s http://127.0.0.1:8080/api/system/health | jq .
```

期望：

- `code` 为 `200`。
- `data.database` 为 `UP`。
- `data.redis` 在 `no-redis` 模式下可为 `DISABLED`。

---

## 6. 启动前端

新开终端：

```bash
cd /Users/nathan/Work/Anti-money-Laundering/frontend
npm install
npm run dev -- --host 127.0.0.1
```

前端成功标志：

- Vite 输出本地地址 `http://127.0.0.1:5173/`。
- 浏览器可打开 `http://127.0.0.1:5173/login`。

---

## 7. 准备 RBAC 测试账号

回到仓库根目录。

root 空密码：

```bash
DB_PASSWORD= bash scripts/prepare-rbac-e2e-users.sh --execute
```

root 使用默认开发密码：

```bash
DB_PASSWORD=aml_dev_123 bash scripts/prepare-rbac-e2e-users.sh --execute
```

准备的账号密码均为 `admin123`：

| 用户名 | 角色 |
|--------|------|
| `admin` | `ROLE_ADMIN` |
| `e2e_compliance` | `ROLE_COMPLIANCE` |
| `e2e_investigator` | `ROLE_INVESTIGATOR` |
| `e2e_viewer` | `ROLE_VIEWER` |

---

## 8. 执行测试

建议新建统一运行编号：

```bash
export E2E_RUN_ID=$(date +%Y%m%d%H%M%S)
```

### 8.1 后端全量测试

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -q test
```

期望：命令退出码为 `0`，无 test failure。

### 8.2 API E2E

```bash
BASE_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$E2E_RUN_ID \
bash scripts/e2e-test.sh
```

期望：

- 登录成功。
- 创建客户、录入交易、筛查、查询预警/案件/报送/产品/系统模块均通过。
- 结果汇总中失败数为 `0`。

### 8.3 前端 smoke E2E

```bash
BASE_URL=http://127.0.0.1:5173 \
API_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$E2E_RUN_ID \
bash scripts/frontend-e2e.sh
```

期望：

- 首页和登录页返回 `200`。
- Vite `/api/system/health` 代理正常。
- 登录 API 返回 `accessToken`。
- Token 刷新成功。
- `/dashboard`、`/system`、`/kyc` 等核心路由可访问。
- 第 6 次错误登录触发 `429` 限流。
- 失败数为 `0`。

### 8.4 前端浏览器 E2E

```bash
FRONTEND_URL=http://127.0.0.1:5173 \
E2E_RUN_ID=$E2E_RUN_ID \
bash scripts/frontend-browser-e2e.sh
```

期望：

- 真实浏览器可登录 `admin/admin123` 并进入 `/dashboard`。
- 刷新后登录态仍保留。
- `/api/auth/me` 返回 `roles`、`permissions`，管理员包含 `ROLE_ADMIN` 与 `system:user`。
- `/dashboard`、`/system`、`/kyc`、`/monitoring`、`/alerts`、`/products` 页面渲染非空。
- 页面无 Vite/Vue 错误覆盖层。
- 访问过程中无新增 `console.error`、`console.warn` 或 `pageerror`。

如本机没有 Chrome/Edge，可先在 `frontend` 目录执行：

```bash
npx playwright install chromium
```

### 8.5 RBAC E2E

```bash
API_URL=http://127.0.0.1:8080/api \
E2E_RUN_ID=$E2E_RUN_ID \
bash scripts/rbac-e2e.sh
```

期望：

- `admin`、`e2e_compliance`、`e2e_investigator`、`e2e_viewer` 均可登录。
- `/auth/me` 返回角色与权限。
- `ROLE_VIEWER` 调用写接口返回 `403`。
- `ROLE_INVESTIGATOR` 不能创建产品，返回 `403`。
- `ROLE_COMPLIANCE` 不能创建系统用户，返回 `403`。
- 调查员创建客户、合规创建产品、管理员创建用户返回 `200`。
- API 新建用户可立即登录。

---

## 9. 浏览器人工核验

打开：

```text
http://127.0.0.1:5173/login
```

使用：

```text
用户名：admin
密码：admin123
```

至少检查：

- 登录后进入 `/dashboard`。
- 刷新页面后登录态仍可用。
- `/system` 页面可打开。
- `/kyc`、`/monitoring`、`/alerts`、`/products` 页面无明显空白和控制台错误。
- 网络请求中 `/api/auth/me` 响应包含 `roles`、`permissions`。

建议 AI Agent 使用浏览器开发者工具或 Playwright 截图保存关键页面证据。

---

## 10. 常见问题处理

### 10.1 Redis 连接失败

现象：

```text
Unable to connect to Redis server: localhost/127.0.0.1:6379
```

处理：使用 `dev,no-redis` profile，并确认启动参数包含：

```text
--aml.kafka.enabled=false
```

### 10.2 Kafka 连接刷屏

现象：

```text
Connection to node -1 (localhost/127.0.0.1:9092) could not be established
```

处理：确认启动参数包含：

```text
--aml.kafka.enabled=false
```

### 10.3 MySQL 密码不匹配

现象：

```text
Access denied for user 'root'
```

处理：

- root 空密码：使用 `--spring.datasource.password=`。
- root 默认开发密码：使用 `--spring.datasource.password=aml_dev_123`。
- 也可自行覆盖 `--spring.datasource.username=` 和 `--spring.datasource.url=`。

### 10.4 登录限流影响后续测试

处理：换一个测试 IP：

```bash
E2E_IP=127.0.0.201 bash scripts/e2e-test.sh
E2E_LIMIT_IP=127.0.0.202 bash scripts/frontend-e2e.sh
```

### 10.5 RBAC 账号不存在

处理：重新执行：

```bash
DB_PASSWORD= bash scripts/prepare-rbac-e2e-users.sh --execute
```

或按实际密码设置 `DB_PASSWORD`。

---

## 11. 清理测试数据

先 dry-run：

```bash
DB_PASSWORD= bash scripts/cleanup-e2e-data.sh
```

确认后执行：

```bash
DB_PASSWORD= bash scripts/cleanup-e2e-data.sh --execute
```

如果 MySQL 使用默认开发密码：

```bash
DB_PASSWORD=aml_dev_123 bash scripts/cleanup-e2e-data.sh --execute
```

---

## 12. 给沙箱外 AI Agent 的建议提示词

可以直接复制以下内容给外部 AI Agent：

```text
你现在在 /Users/nathan/Work/Anti-money-Laundering 仓库中执行测试。

请按 docs/development/沙箱外AI-Agent测试指引.md 完成真实服务验收：
1. 确认 Java 21、Maven、Node/npm、MySQL、jq、curl 可用。
2. 准备 aml_system 数据库。
3. 用 dev,no-redis profile 启动后端，并显式设置 --aml.kafka.enabled=false。
4. 启动 frontend Vite 服务。
5. 准备 RBAC E2E 账号。
6. 依次运行 mvn -q test、scripts/e2e-test.sh、scripts/frontend-e2e.sh、scripts/frontend-browser-e2e.sh、scripts/rbac-e2e.sh。
7. 用浏览器登录 admin/admin123，复核 dashboard/system/kyc/monitoring 等页面。
8. 记录每条命令、退出码、失败响应、关键截图路径。
9. 不要修改业务代码；如发现问题，只提交测试报告和最小复现信息。

最终输出：
- 环境信息
- 启动命令
- 测试命令和结果
- 失败项详情
- 浏览器核验截图
- 是否建议进入下一阶段
```

---

## 13. 测试报告模板

```markdown
# AML 沙箱外 E2E 测试报告

## 环境

- 日期：
- 机器：
- Java：
- Maven：
- Node/npm：
- MySQL：
- 后端 profile：

## 服务启动

- 后端启动命令：
- 前端启动命令：
- 后端健康检查结果：
- 前端访问结果：

## 自动化测试

| 测试项 | 命令 | 结果 | 备注 |
|--------|------|------|------|
| 后端全量测试 | `mvn -q test` | PASS/FAIL | |
| API E2E | `scripts/e2e-test.sh` | PASS/FAIL | |
| 前端 smoke | `scripts/frontend-e2e.sh` | PASS/FAIL | |
| 前端浏览器 E2E | `scripts/frontend-browser-e2e.sh` | PASS/FAIL | |
| RBAC E2E | `scripts/rbac-e2e.sh` | PASS/FAIL | |

## 浏览器核验

- `/login`：
- `/dashboard`：
- `/system`：
- `/kyc`：
- `/monitoring`：
- `/api/auth/me` 是否包含 roles/permissions：

## 问题清单

| 严重级别 | 问题 | 复现步骤 | 实际结果 | 期望结果 | 日志/截图 |
|----------|------|----------|----------|----------|-----------|
| P0/P1/P2 | | | | | |

## 结论

- 是否通过本轮验收：
- 是否可进入下一阶段：
- 建议下一步：
```
