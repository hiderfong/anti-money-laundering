# P2-E — 控制器读端点授权全量加固

> 日期：2026-06-16
> 代码基线：`32d9fe0` (main)
> 关联：`docs/development/全量分析报告-20260616.md`（P2-E）；延续 P1-A（`7d932aa`）
> 状态：设计已确认（审计完成、范围已选"全部 30 读端点"），待 spec review → writing-plans

---

## 1. 背景与目标

P1-A 修复了审计日志/监控的几个端点授权。本次对全部 27 个控制器做源码逐行授权审计，发现一个**系统性模式**：写端点（POST/PUT）普遍有 `@PreAuthorize`，但读端点（GET）系统性缺失。安全基线是 `anyRequest().authenticated()`，故缺注解的读端点对**任意已登录用户（任意角色）开放**——对矩阵中 view 权限受限的模块，这是真实越权读取。

**目标**：为全部缺授权的读端点补 `@PreAuthorize`，授权码严格对齐 `RBAC角色权限矩阵.md`；配 RBAC 403 集成测试证明越权读被拒。纯增量，不改业务逻辑。

**已确认范围**：全部 30 个读端点（高危 20 + 低危 10），覆盖 12 个控制器。

---

## 2. 审计方法与口径

- 源码逐行解析（剥离 `/* */` 与 `//` 注释后按"方法注解块"判定），不依赖 `@GetMapping` 计数（吸取 6-16 误报教训）。
- 每个端点判定为三类：已授权 / 有意公开（矩阵"登录即可"或 `PERMIT_ALL_PATHS` 或类级注解）/ 真实缺口。
- 授权码由矩阵页面路由表唯一确定（每模块一个 view 权限）。

**有意公开、确认无需改动**：Auth、Health（PERMIT_ALL_PATHS）；Dashboard、Notification（矩阵"登录即可"）；UserController（类级 `@PreAuthorize`）；DictController 的 listDicts/getDictItems（字典下拉参考数据，全体登录用户可读，保持现状）。

---

## 3. 修复清单（30 端点，授权码取自矩阵）

所有授权统一形式：`@PreAuthorize("hasRole('ADMIN') or hasAuthority('<perm>')")`（与现有写端点及 P1-A 一致；ADMIN 始终放行）。

### 高危（矩阵 view 限部分角色，存在越权读）— 20 端点 / 9 控制器

| 控制器 | 端点 | 权限 |
|--------|------|------|
| ScreeningController | GET /results, /whitelist | `screening:view` |
| GraphController | GET /ring-detection, /multi-layer-transfer, /shared-accounts, /network-density | `monitoring:view` |
| RuleController | GET /page, /{id}/versions | `monitoring:view` |
| TransactionController | GET /page, /daily-summary | `monitoring:view` |
| SelfAssessmentController | GET /{id}, /list | `assessment:view` |
| RectificationController | GET /list | `assessment:view` |
| StrReportController | GET /page, /{id} | `report:str` |
| ReportingController | GET /large-txn/page, /large-txn/{id}/xml | `report:view` |
| ProductController | GET /{id}, /page, /{id}/assessments | `product:view` |

### 低危（矩阵 view 全角色，一致性/纵深防御）— 10 端点 / 3 控制器

| 控制器 | 端点 | 权限 |
|--------|------|------|
| CustomerController | GET /{id}, /page, /{id}/360, /{id}/relationship-graph | `customer:view` |
| AlertController | GET /page, /{id}, /{id}/disposition-chain, /statistics | `alert:view` |
| CaseController | GET /{id}, /page | `case:view` |

**授权码存在性**：`screening:view`、`monitoring:view`、`assessment:view`、`report:str`、`report:view`、`product:view`、`customer:view`、`alert:view`、`case:view` 均已在 V003 RBAC 种子中定义并授予相应角色，且已被各模块写端点使用——非自创。

---

## 4. 边界与一致性

- 注解风格：方法级（匹配代码库 26 个控制器既有约定）；插入位置 `@*Mapping` → `@Operation` → `@PreAuthorize` → 方法（同 P1-A）。
- import：各控制器若未引入 `org.springframework.security.access.prepost.PreAuthorize` 则补。
- 不动写端点、不动业务逻辑、不动有意公开端点。
- StrReport 读用 `report:str`（与该模块页面路由及写端点 create 的 `report:str` 一致）；Reporting（大额报送）读用 `report:view`（页面路由 /reporting → report:view）。

---

## 5. 测试

沿用 `RbacIntegrationTest` 的 `@WithMockUser(authorities={...})` + 断言 403 模式（无需 DB 种子）。每个 view 权限受限模块加 1 条"无该权限角色访问读端点 → 403"用例（覆盖高危的越权读）：

| 用例 | 模拟用户 | 端点 | 期望 |
|------|---------|------|------|
| screening 读越权 | ROLE_VIEWER（无 screening:view） | GET /screening/results | 403 |
| monitoring 读越权 | ROLE_VIEWER（无 monitoring:view） | GET /monitoring/transactions/page | 403 |
| assessment 读越权 | ROLE_INVESTIGATOR（无 assessment:view） | GET /assessments/list | 403 |
| str-report 读越权 | ROLE_INVESTIGATOR（无 report:str） | GET /str-reports/page | 403 |
| reporting 读越权 | ROLE_INVESTIGATOR（无 report:view） | GET /reporting/large-txn/page | 403 |
| product 读越权 | ROLE_INVESTIGATOR（无 product:view） | GET /products/page | 403 |

低危模块（customer/alert/case，全角色有 view）不强加 403 用例（无可越权的角色可断言）；其授权加固靠编译 + 既有集成测试不回归保证。可选加 1 条"有 view 权限 → 不 403"正向用例。

---

## 6. 验收标准

1. §3 全部 30 个读端点均加 `@PreAuthorize`，授权码与表格一致。
2. 6 个高危模块各有 1 条越权读→403 的 RBAC 集成测试，全绿。
3. 写端点、有意公开端点、业务逻辑零改动。
4. 全量 `mvn test` 保持绿（当前 269；新增 6 条 RBAC 用例 → 预期 275）。
5. 附带结构发现（GraphController 与 GraphAnalysisController 重复）记录为后续 backlog，本轮不处理。

---

## 7. 非目标（本轮不做）

- GraphController/GraphAnalysisController 重复控制器的去重（结构问题，单列 backlog）。
- DictController 字典读端点加权限（有意保持登录即可）。
- 写端点授权复核（已普遍覆盖）。
