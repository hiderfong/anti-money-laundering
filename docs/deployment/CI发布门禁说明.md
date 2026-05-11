# CI 发布门禁说明

本文档说明当前 Actions 发布门禁的覆盖范围、触发方式和失败排查路径。

当前仓库远端为本机 Gitea，因此实际生效的主工作流文件为 `.gitea/workflows/ci.yml`。`.github/workflows/ci.yml` 保持同等配置，用于未来镜像到 GitHub 时直接复用。

本机 Gitea 的 job 容器无法稳定访问以 `localhost` 生成的 artifact 上传地址，因此 `.gitea/workflows/ci.yml` 不上传 artifact，而是在日志中列出后端测试报告、前端构建产物和浏览器 E2E 截图路径。GitHub 镜像工作流仍保留 artifact 上传。

Gitea job 容器是冷启动环境，Maven 和 npm 首次解析依赖可能受到网络抖动影响。`.gitea/workflows/ci.yml` 使用 `scripts/ci-maven.sh` 包装 Maven 命令，使用 `scripts/ci-npm.sh` 包装 npm 命令，默认重试 3 次；Maven 重试前会清理 `.lastUpdated` 传输标记，npm 重试前会校验本地缓存，避免半开连接拖满整条门禁。

由于当前本机 Docker/Colima 环境对 `repo.maven.apache.org` 的 DNS 解析不稳定，`scripts/ci-maven.sh` 默认给 Maven Central 配置 `https://maven.aliyun.com/repository/public` 镜像源。可通过 `CI_MAVEN_MIRROR_URL` 替换为企业 Nexus/Artifactory，或设置 `CI_MAVEN_USE_DEFAULT_MIRROR=false` 禁用默认镜像。

本机 Gitea runner 默认使用 `docker/gitea-actions-runner/Dockerfile` 构建预热 job 镜像 `aml-gitea-job:latest`，其中预装 JDK 21、Maven、MySQL client、jq 以及 Playwright Chromium 依赖。`.gitea/workflows/ci.yml` 会在工具链已存在时跳过 `apt-get` 冷安装，避免发布门禁耗时受外部包仓库下载速度影响。

为降低本机 Gitea 对外网 GitHub 的依赖，`.gitea/workflows/ci.yml` 使用预装在 job 镜像中的 `aml-ci-checkout` 直接从本机 Gitea 拉取当前提交，不再下载 `actions/checkout`。该命令由仓库内的 `scripts/ci-checkout.sh` 构建进 `aml-gitea-job:latest`。

Docker 镜像构建使用 BuildKit Maven cache，不再单独执行 `dependency:go-offline`。这样可以避免冷启动 runner 在 Docker 构建阶段重复长时间解析依赖，同时仍通过 `scripts/ci-maven.sh clean package -DskipTests` 校验镜像内可完成应用打包。

## 触发方式

- 推送到 `main`
- 推送到 `codex/**`
- 向 `main` 发起 Pull Request
- 在 Gitea/GitHub Actions 页面手动触发 `workflow_dispatch`

当前本机 Gitea 必须注册至少一个 Actions runner，否则任务会停留在 `queued`。

## 门禁任务

| 任务 | 目的 | 关键命令 |
| --- | --- | --- |
| `backend-test` | 校验后端单元测试和集成测试 | `scripts/ci-maven.sh test` |
| `frontend-build` | 校验前端依赖锁定和生产构建 | `scripts/ci-npm.sh ci`, `npm run build` |
| `prod-readiness` | 防止占位符配置进入生产发布 | `scripts/prod-readiness-check.sh` |
| `container-build` | 校验 Dockerfile 可构建应用镜像 | `docker build` |
| `e2e` | 启动 MySQL、后端、前端并执行全量端到端回归 | `scripts/e2e-test.sh`, `scripts/frontend-e2e.sh`, `scripts/frontend-browser-e2e.sh`, `scripts/rbac-e2e.sh` |

`e2e` 依赖前置三个任务全部通过后才会运行，因此它可以作为发布前的最终回归门禁。

## E2E 环境

CI 中的 E2E 使用 `ubuntu-latest` runner，并通过 service container 启动 `mysql:8.0`。

运行时配置：

- 后端：`dev,no-redis,no-es` profile，禁用 Kafka/Redis/Redisson，连接 CI MySQL。
- 启动方式：Gitea E2E 先通过 `scripts/ci-maven.sh -DskipTests package` 打包，再用 `java -jar target/aml-system-1.0.0-SNAPSHOT.jar` 启动后端，避免启动期再次访问 Maven Central。
- 前端：Vite dev server，监听 `127.0.0.1:5173`。
- 浏览器：Playwright Chromium，默认 headless。
- 测试数据隔离：使用 `E2E_RUN_ID=ci-${{ github.run_id }}-${{ github.run_attempt }}`。

CI 中还会显式设置 `AML_REDIS_ENABLED=false`、`AML_NEO4J_ENABLED=false` 与合并后的 `SPRING_AUTOCONFIGURE_EXCLUDE`，确保 Spring Boot 启动不会加载 Redis/Redisson、Neo4j、Elasticsearch 自动配置。

## Gitea Runner

查看当前 Actions 状态：

```bash
bash scripts/gitea-actions-status.sh
```

首次启动本机 runner：

```bash
bash scripts/start-gitea-actions-runner.sh
```

该脚本会：

- 从 `origin` 远端解析 Gitea 地址和仓库路径。
- 通过 Gitea API 获取仓库 runner 注册令牌。
- 启动 `gitea/act_runner` Docker 容器。
- 构建本机预热 job 镜像 `aml-gitea-job:latest`。
- 注册 `ubuntu-latest` 标签，供 `.gitea/workflows/ci.yml` 使用。
- 默认重置 runner 数据卷，确保标签或 job 镜像变更能重新注册生效。

如果 Gitea 不是通过 `localhost` 暴露，或 Docker 容器无法访问宿主机 Gitea，可通过环境变量覆盖：

```bash
GITEA_RUNNER_INSTANCE_URL=http://host.docker.internal:3333/ \
GITEA_RUNNER_LABELS='ubuntu-latest:docker://aml-gitea-job:latest' \
bash scripts/start-gitea-actions-runner.sh
```

如果仅想重启 runner 且确认标签未变化，可设置 `GITEA_RUNNER_RESET_VOLUME=false` 保留原注册数据。

## 失败排查

优先查看失败任务的日志：

- 后端启动失败：查看 `Show service logs on failure` 中的 `/tmp/aml-backend.log`。
- 前端启动失败：查看 `/tmp/aml-frontend.log`。
- 浏览器 E2E 失败：在 `List E2E screenshots` 步骤日志中查看截图路径，结合失败步骤日志定位页面状态；GitHub 镜像流水线可下载 `frontend-browser-e2e-screenshots` artifact。
- 生产配置门禁失败：确认 `.env.example` 仍保留占位符，并确认有效生产配置样例能通过 `scripts/prod-readiness-check.sh`。
- 任务长期 `queued`：运行 `bash scripts/gitea-actions-status.sh`，确认 runner 数量不为 0 且包含 `ubuntu-latest` 标签。

本地复现建议按 CI 顺序执行：

```bash
bash scripts/ci-maven.sh test
(cd frontend && bash ../scripts/ci-npm.sh ci)
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
- Dockerfile 可成功构建应用镜像。
- 全量 E2E 脚本通过。
- 浏览器截图或截图路径日志无异常页面状态。
