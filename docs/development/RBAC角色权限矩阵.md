# RBAC 角色权限矩阵

> 版本：v1.0  
> 日期：2026-05-10  
> 数据来源：`V002__init_data.sql`、`V003__rbac_roles_permissions.sql`、前端 `router/index.ts`

---

## 1. 角色定义

| 角色编码 | 角色名称 | 定位 | E2E 测试账号 |
|----------|----------|------|--------------|
| `ROLE_ADMIN` | 系统管理员 | 系统配置、用户权限、全部业务操作 | `e2e_admin` |
| `ROLE_COMPLIANCE` | 合规专员 | 客户、筛查、交易、预警、案件、报送、产品、自评估 | `e2e_compliance` |
| `ROLE_INVESTIGATOR` | 调查员 | 客户、筛查、交易、预警、案件调查 | `e2e_investigator` |
| `ROLE_VIEWER` | 只读用户 | 只读查看部分模块 | `e2e_viewer` |

E2E 测试账号由脚本准备：

```bash
scripts/prepare-rbac-e2e-users.sh --execute
```

默认密码为 `admin123`，仅用于本地/dev 测试库。

---

## 2. 页面路由权限

| 页面 | 路由 | 所需权限 | ADMIN | COMPLIANCE | INVESTIGATOR | VIEWER |
|------|------|----------|-------|------------|--------------|--------|
| 仪表盘 | `/dashboard` | 登录即可 | 是 | 是 | 是 | 是 |
| 客户管理 | `/kyc` | `customer:view` | 是 | 是 | 是 | 是 |
| 客户详情 | `/kyc/:id` | `customer:view` | 是 | 是 | 是 | 是 |
| 名单筛查 | `/screening` | `screening:view` | 是 | 是 | 是 | 否 |
| 交易监测 | `/monitoring` | `monitoring:view` | 是 | 是 | 是 | 否 |
| 预警管理 | `/alerts` | `alert:view` | 是 | 是 | 是 | 是 |
| 案件管理 | `/cases` | `case:view` | 是 | 是 | 是 | 是 |
| STR 报告 | `/str-reports` | `report:str` | 是 | 是 | 否 | 否 |
| 监管报送 | `/reporting` | `report:view` | 是 | 是 | 否 | 是 |
| 产品管理 | `/products` | `product:view` | 是 | 是 | 否 | 是 |
| 自评估 | `/assessment` | `assessment:view` | 是 | 是 | 否 | 否 |
| 通知中心 | `/notifications` | 登录即可 | 是 | 是 | 是 | 是 |
| 系统管理 | `/system` | `system:view` | 是 | 否 | 否 | 否 |

验收规则：

- 有权限：菜单可见，直接访问路由不跳转 403。
- 无权限：菜单不可见，直接访问路由跳转 `/403`。
- 管理员：前端 `isAdmin` 放行所有受保护页面。

---

## 3. 按钮/动作权限

| 权限编码 | 业务动作 | ADMIN | COMPLIANCE | INVESTIGATOR | VIEWER |
|----------|----------|-------|------------|--------------|--------|
| `customer:view` | 查看客户 | 是 | 是 | 是 | 是 |
| `customer:create` | 新增客户 | 是 | 是 | 是 | 否 |
| `customer:update` | 编辑客户 | 是 | 是 | 是 | 否 |
| `customer:delete` | 删除客户 | 是 | 否 | 否 | 否 |
| `customer:export` | 导出客户 | 是 | 是 | 否 | 否 |
| `screening:view` | 查看筛查 | 是 | 是 | 是 | 否 |
| `screening:execute` | 执行筛查 | 是 | 是 | 是 | 否 |
| `monitoring:view` | 查看交易监测 | 是 | 是 | 是 | 否 |
| `monitoring:config` | 监测规则配置 | 是 | 是 | 否 | 否 |
| `alert:view` | 查看预警 | 是 | 是 | 是 | 是 |
| `alert:process` | 处理预警 | 是 | 是 | 是 | 否 |
| `alert:dismiss` | 排除预警 | 是 | 是 | 否 | 否 |
| `case:view` | 查看案件 | 是 | 是 | 是 | 是 |
| `case:create` | 创建案件 | 是 | 是 | 是 | 否 |
| `case:approve` | 审批案件 | 是 | 是 | 否 | 否 |
| `report:view` | 查看报告 | 是 | 是 | 否 | 是 |
| `report:str` | STR 报告 | 是 | 是 | 否 | 否 |
| `report:submit` | 报告报送 | 是 | 是 | 否 | 否 |
| `product:view` | 查看产品 | 是 | 是 | 否 | 是 |
| `product:manage` | 管理产品 | 是 | 是 | 否 | 否 |
| `assessment:view` | 查看自评估 | 是 | 是 | 否 | 否 |
| `assessment:manage` | 管理自评估 | 是 | 是 | 否 | 否 |
| `system:view` | 查看系统管理 | 是 | 否 | 否 | 否 |
| `system:user` | 用户管理 | 是 | 否 | 否 | 否 |
| `system:role` | 角色管理 | 是 | 否 | 否 | 否 |

---

## 4. API 权限验收清单

当前前端已实现菜单、路由、按钮权限基础；后端核心写接口已补充 API 级权限校验。验收应至少覆盖：

| API 类型 | 示例 | 应允许角色 | 应拒绝角色 |
|----------|------|------------|------------|
| 客户写入 | `POST /kyc/customers` | ADMIN、COMPLIANCE、INVESTIGATOR | VIEWER |
| 筛查执行 | `POST /screening/screen` | ADMIN、COMPLIANCE、INVESTIGATOR | VIEWER |
| 预警处理 | `POST /alerts/process` | ADMIN、COMPLIANCE、INVESTIGATOR | VIEWER |
| 案件创建 | `POST /cases` | ADMIN、COMPLIANCE、INVESTIGATOR | VIEWER |
| STR 报告 | `POST /str-reports` | ADMIN、COMPLIANCE | INVESTIGATOR、VIEWER |
| 报告提交 | `POST /reporting/large-txn/{id}/submit` | ADMIN、COMPLIANCE | INVESTIGATOR、VIEWER |
| 产品管理 | `POST /products` | ADMIN、COMPLIANCE | INVESTIGATOR、VIEWER |
| 系统用户 | `POST /system/users` | ADMIN | COMPLIANCE、INVESTIGATOR、VIEWER |

后端验收标准：

- 无权限写接口返回 403。
- 只读角色可以访问允许的查询接口，但不能调用任何写接口。
- 自动化测试应分别用 `e2e_admin`、`e2e_compliance`、`e2e_investigator`、`e2e_viewer` 登录并断言。

---

## 5. 当前差距

| 差距 | 影响 | 后续任务 |
|------|------|----------|
| 后端权限已覆盖核心写接口，但尚未沉淀统一权限注解/切面 | 后续扩展接口时容易漏标 | 抽象统一权限注解，并在接口测试中强制覆盖新增写接口 |
| 系统角色管理暂无前端/后端完整角色 API | 多角色准备目前依赖数据库脚本 | 后续完善角色查询和角色分配页面 |
| 系统用户状态枚举仍分散在字符串中 | 后续维护时可能再次出现状态不一致 | 统一 `ENABLED/DISABLED/LOCKED` 用户状态枚举 |
| 浏览器级权限 E2E 未落地 | 只能人工确认菜单/路由 | 引入真实 UI E2E 后补四角色断言 |
