# GitHub 仓库与 CI 说明

> 版本：v1.0
> 日期：2026-05-11
> 适用仓库：`hiderfong/anti-money-laundering`

---

## 1. 仓库状态

| 项目 | 状态 |
|------|------|
| GitHub 仓库 | `https://github.com/hiderfong/anti-money-laundering` |
| 可见性 | 私有仓库 |
| 主分支 | `main` |
| 当前基线 tag | `v0.1.0-ci-green` |
| 当前基线 Release | `https://github.com/hiderfong/anti-money-laundering/releases/tag/v0.1.0-ci-green` |
| GitHub Actions 基线运行 | `https://github.com/hiderfong/anti-money-laundering/actions/runs/25652700780` |
| 历史保留 | GitHub `main` 已切换为本地完整 Git 历史 |
| API 快照备份 | `github-snapshot-20260511-134740` |

本项目当前保留两个远端：

| Remote | 用途 |
|--------|------|
| `origin` | 本地 Gitea 仓库，用于本机/局域网内 CI 与备份 |
| `github` | GitHub 私有仓库，用于云端 Actions、Release 与远程协作 |

> 注意：文档中不要记录任何 token、PAT、远端密码或真实生产密钥。

---

## 2. GitHub Actions 工作流

工作流文件：

```text
.github/workflows/ci.yml
```

触发方式：

- 推送到 `main`
- 推送到 `codex/**`
- 针对 `main` 的 Pull Request
- 手动 `workflow_dispatch`

当前质量门包含 5 个 job：

| Job | 说明 |
|-----|------|
| `Backend Unit And Integration Tests` | 后端 Maven 单元测试与集成测试 |
| `Frontend Build` | 前端依赖安装与生产构建 |
| `Production Readiness Checks` | 生产环境变量、脚本语法与部署前检查 |
| `End-To-End Release Gate` | MySQL service + 后端 + 前端 + API/浏览器/RBAC E2E |
| `Container Build` | Docker 应用镜像构建 |

`End-To-End Release Gate` 覆盖：

- `scripts/e2e-test.sh`
- `scripts/frontend-e2e.sh`
- `scripts/frontend-browser-e2e.sh`
- `scripts/prepare-rbac-e2e-users.sh --execute`
- `scripts/rbac-e2e.sh`

---

## 3. 当前基线

当前 GitHub CI 全绿基线：

| 项目 | 值 |
|------|----|
| tag | `v0.1.0-ci-green` |
| commit | `9d90f354ce1be8203b0295639f32fd08d8464815` |
| commit message | `test: keep rbac e2e product code short` |
| GitHub Actions run | `#4` |
| run URL | `https://github.com/hiderfong/anti-money-laundering/actions/runs/25652700780` |
| Release URL | `https://github.com/hiderfong/anti-money-laundering/releases/tag/v0.1.0-ci-green` |

基线结论：

- GitHub `main` 已可通过标准 HTTPS Git 凭据推送。
- GitHub `main` 已包含本地完整 Git 历史，不再只是 API 上传快照。
- GitHub Actions run `#4` 五个 job 全部通过。
- 当前仓库保持私有，因此 GitHub 免费账户下暂不能启用分支保护。

---

## 4. 凭据管理

GitHub token 不应写入：

- 聊天记录
- 文档
- 代码
- `.env`
- Git remote URL
- CI 日志

推荐使用 macOS Keychain 保存 GitHub HTTPS 凭据：

```bash
cd /Users/nathan/Work/Anti-money-Laundering

printf "GitHub token: "
stty -echo
IFS= read -r GH_TOKEN
stty echo
printf "\n"

printf "protocol=https\nhost=github.com\nusername=x-access-token\npassword=%s\n\n" "$GH_TOKEN" | git credential-osxkeychain store

unset GH_TOKEN
```

验证 GitHub 凭据：

```bash
GIT_TERMINAL_PROMPT=0 git ls-remote github refs/heads/main
```

正常情况下应返回 GitHub `main` 的提交 SHA，例如：

```text
9d90f354ce1be8203b0295639f32fd08d8464815 refs/heads/main
```

---

## 5. 日常推送流程

推送本地 Gitea：

```bash
git push origin main
```

推送 GitHub：

```bash
git push github main
```

推送 tag：

```bash
git push origin v0.1.0-ci-green
git push github v0.1.0-ci-green
```

建议每次推送 GitHub 后检查 Actions：

```text
https://github.com/hiderfong/anti-money-laundering/actions
```

---

## 6. 分支保护限制

当前 GitHub 仓库保持私有。GitHub 免费账户下，私有仓库启用 branch protection 会返回如下限制：

```text
Upgrade to GitHub Pro or make this repository public to enable this feature.
```

影响：

- 暂时不能强制要求 `main` 必须通过 Actions 才能合入。
- 暂时不能从 GitHub 侧禁止直接 push 或 force push。
- Actions 仍会正常运行并反馈质量状态。

当前建议：

- 在项目完善期保持私有仓库。
- 继续使用 GitHub Actions 作为质量反馈。
- 重要节点使用 tag 和 Release 固化基线。
- 准备对外展示或多人协作前，再选择公开仓库或升级 GitHub Pro 以启用分支保护。

---

## 7. 公开仓库前检查

如果未来需要公开仓库，至少执行以下检查：

```bash
rg -n --hidden --glob '!.git/**' --glob '!node_modules/**' --glob '!frontend/node_modules/**' --glob '!target/**' --glob '!frontend/dist/**' \
  'github_pat_|ghp_|gho_|ghu_|ghs_|ghr_|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----' .

git log --all --oneline -Sgithub_pat_ -- .
git log --all --oneline -S'-----BEGIN' -- .
git log --all --oneline -S'AKIA' -- .
```

还需要人工确认：

- `.env` 未被提交。
- 文档中没有真实客户数据。
- CI 日志没有输出 token。
- 开发默认密码仅用于本地、测试或示例环境。
- 生产部署必须使用 `.env` 中的真实强密钥，并通过 `scripts/prod-readiness-check.sh`。
