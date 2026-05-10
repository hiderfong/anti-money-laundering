# CI 发布门禁说明

本文档说明当前 GitHub Actions 发布门禁的覆盖范围、触发方式和失败排查路径。对应工作流文件为 `.github/workflows/ci.yml`。

## 触发方式

- 推送到 `main`
- 推送到 `codex/**`
- 向 `main` 发起 Pull Request
- 在 GitHub Actions 页面手动触发 `workflow_dispatch`

同一分支上的新任务会自动取消旧任务，避免重复消耗 CI 资源。

## 门禁任务

| 任务 | 目的 | 关键命令 |
| --- | --- | --- |
| `backend-test` | 校验后端单元测试和集成测试 | `mvn -q test` |
| `frontend-build` | 校验前端依赖锁定和生产构建 | `npm ci`, `npm run build` |
| `prod-readiness` | 防止占位符配置进入生产发布 | `scripts/prod-readiness-check.sh` |
| `e2e` | 启动 MySQL、后端、前端并执行全量端到端回归 | `scripts/e2e-test.sh`, `scripts/frontend-e2e.sh`, `scripts/frontend-browser-e2e.sh`, `scripts/rbac-e2e.sh` |

`e2e` 依赖前置三个任务全部通过后才会运行，因此它可以作为发布前的最终回归门禁。

## E2E 环境

CI 中的 E2E 使用 GitHub 托管 Ubuntu runner，并通过 service container 启动 `mysql:8.0`。

运行时配置：

- 后端：`dev,no-redis` profile，禁用 Kafka，连接 CI MySQL。
- 前端：Vite dev server，监听 `127.0.0.1:5173`。
- 浏览器：Playwright Chromium，默认 headless。
- 测试数据隔离：使用 `E2E_RUN_ID=ci-${{ github.run_id }}-${{ github.run_attempt }}`。

## 失败排查

优先查看失败任务的日志：

- 后端启动失败：查看 `Show service logs on failure` 中的 `/tmp/aml-backend.log`。
- 前端启动失败：查看 `/tmp/aml-frontend.log`。
- 浏览器 E2E 失败：下载 `frontend-browser-e2e-screenshots` artifact，结合失败步骤日志定位页面状态。
- 生产配置门禁失败：确认 `.env.example` 仍保留占位符，并确认有效生产配置样例能通过 `scripts/prod-readiness-check.sh`。

本地复现建议按 CI 顺序执行：

```bash
mvn -q test
npm --prefix frontend ci
npm --prefix frontend run build
bash scripts/prod-readiness-check.sh /path/to/prod.env
bash scripts/e2e-test.sh
bash scripts/frontend-e2e.sh
bash scripts/frontend-browser-e2e.sh
bash scripts/rbac-e2e.sh
```

## 发布判定

满足以下条件时，可认为本轮发布门禁通过：

- 后端测试通过。
- 前端构建通过。
- 生产配置校验通过，且 `.env.example` 被正确拒绝。
- 全量 E2E 脚本通过。
- 浏览器截图 artifact 无异常页面状态。
