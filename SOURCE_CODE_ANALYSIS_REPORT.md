# 反洗钱系统（AML）源码分析报告

> 分析路径：`/Users/nathan/Work/Anti-money-Laundering`  
> 分析日期：2026-05-04

---

## 一、项目概况

| 项目 | 内容 |
|------|------|
| **系统名称** | Insurance AML System（保险公司反洗钱管理系统） |
| **架构模式** | 前后端分离 |
| **后端技术** | Spring Boot 3.4 + JDK 21 + MyBatis-Plus + Spring Security + JWT |
| **前端技术** | Vue 3 + TypeScript + Vite + Element Plus + Pinia |
| **中间件** | MySQL 8 + Redis 7 + Kafka 3.6 + Elasticsearch 8.12 + Neo4j + MinIO |
| **代码规模** | Java 后端约 250 个文件，前端约 30 个源文件 |

---

## 二、架构亮点

1. **技术选型先进**：采用 JDK 21 虚拟线程、Spring Boot 3.4、Vue 3 组合式 API 等主流技术栈
2. **多引擎规则评估**：集成 Drools 规则引擎、Redis Lua 脚本、机器学习（Smile Isolation Forest）三种规则评估方式
3. **基础设施完善**：Docker Compose 一键启动全部中间件，Flyway 数据库版本管理
4. **AOP 横切关注点**：通过自定义注解 `@AuditLog` 和 `@MaskField` 实现审计日志和数据脱敏
5. **响应式设计**：前端支持暗黑模式、移动端适配

---

## 三、数据库设计

共 **34 张表**，覆盖 9 大业务域：
- 系统管理（11张）
- KYC 客户身份识别（6张）
- 名单筛查（8张）
- 产品风险评估（2张）
- 保单/交易（3张）
- 规则引擎（3张）
- 预警/案件/STR 报告（8张）
- 监管报送（3张）
- 风险自评估（4张）

设计规范：全部 InnoDB + utf8mb4，统一审计字段，应用层加密敏感字段。

---

## 四、后端代码分析

### 4.1 优点

- **全局异常处理完善**：`GlobalExceptionHandler` 覆盖业务异常、参数校验、安全异常、类型转换异常等
- **日志体系规范**：统一 SLF4J + Logback，结构化输出，按环境分级
- **Drools 规则配置化**：8 条可疑交易规则外部化到 `.drl` 文件，便于业务人员调整
- **异步架构合理**：使用虚拟线程线程池处理并发任务

### 4.2 🔴 高危问题

#### 1. 硬编码敏感信息（严重）

早期 `application-dev.yml` 中曾存在开发环境明文密码和密钥：

```yaml
jwt:
  secret: <redacted-dev-jwt-secret>
encryption:
  key: <redacted-dev-encryption-key>
spring.datasource.password: <redacted-dev-db-password>
```

**风险**：密钥泄露可导致 JWT 伪造、数据解密。  
**建议**：使用环境变量或 KMS/Vault 管理。

#### 2. CORS 配置过度宽松（严重）

```java
configuration.setAllowedOriginPatterns(List.of("*"));
configuration.setAllowCredentials(true);
```

`允许所有来源 + 允许凭证` 是危险的 CORS 配置，任何网站都可携带用户凭证发起跨域请求。

#### 3. 制裁名单筛查存在严重性能问题

`ScreeningServiceImpl` 加载全表制裁名单到内存后逐条循环匹配，且内部存在 **N+1 查询**。名单量大时将导致：
- 内存溢出
- 数据库连接池耗尽
- 单次筛查耗时随名单数量线性增长

#### 4. 多处 N+1 查询

- `AlertServiceImpl.pageQueryAlerts()`：分页查询预警后，每行单独查询规则明细
- `CaseServiceImpl.pageQueryCases()`：每行案件单独查询调查记录和 STR 报告数

#### 5. 异步方法事务边界不清

```java
public CompletableFuture<Transaction> ingestTransactionAsync(...) {
    transactionMapper.insert(transaction);  // 不在事务中
    CompletableFuture.runAsync(() -> updateDailySummary(transaction)); // 独立线程无事务
}
```

数据库写入不在事务保护下，失败时无法回滚。

#### 6. Kafka 消息与数据库事务不一致

在 `@Transactional` 方法内发送 Kafka 消息，若数据库回滚但消息已发出，会导致数据与事件不一致。

### 4.3 🟡 中危问题

| 问题 | 说明 |
|------|------|
| 混合字段注入与构造器注入 | 部分 Service 使用 `@Autowired` 字段注入，不利于测试 |
| 循环依赖 | `alert` 与 `case_` 模块间存在循环依赖，使用 `@Lazy` 掩盖 |
| 跨模块直接调用 Mapper | `screening` 模块直接注入 `kyc` 的 `CustomerMapper`，违反模块化原则 |
| 缓存利用率低 | Redis 配置完善，但业务代码几乎未使用 `@Cacheable` |
| 加密工具类不安全 | 密钥长度不足时补零填充，使用平台默认字符集 |
| 事务过长 | `batchProcess` 方法批量处理预警，所有操作在一个大事务中 |

### 4.4 测试覆盖率（严重不足）

- 主代码 250 个文件，测试代码仅 14 个
- **未覆盖的核心模块**：认证、案件流转、交易异步管道、四引擎规则评估、图分析、ML 模型、JWT 过滤器等
- 部分测试使用 `System.out.println` + `return` 跳过，而非断言

---

## 五、前端代码分析

### 5.1 优点

- 使用 Vue 3 Composition API + Pinia + TypeScript，技术栈现代
- 路径别名配置规范
- `tsconfig` 启用了 `noUncheckedIndexedAccess`，增强索引安全性

### 5.2 🔴 高危问题

#### 1. 完全缺乏权限控制

- 路由守卫仅检查 Token 是否存在，**不验证角色/权限**
- `MainLayout.vue` 的侧边栏菜单是**硬编码的**，所有登录用户都能看到所有菜单
- 页面内的增删改查按钮无任何权限控制
- **作为金融合规系统，这是重大缺陷**

#### 2. ECharts 内存泄漏

```typescript
window.addEventListener('resize', () => chart.resize())
```

组件卸载时未 `removeEventListener` 且未 `chart.dispose()`，每次进入仪表盘都新增监听器。

#### 3. Token 明文存储在 localStorage

```typescript
localStorage.setItem('aml_token', data.accessToken)
```

XSS 漏洞可直接窃取 Token。  
**建议**：使用 `httpOnly` Cookie，或至少评估迁移方案。

### 5.3 🟡 严重代码质量问题

#### 1. 过度使用 `any`

几乎所有 API 参数、响应、列表数据都使用 `any`，丧失了 TypeScript 的类型安全价值：

```typescript
create(data: any) { ... }
const customers = ref<any[]>([])
const res: any = await request.get(...)
```

#### 2. 大量代码重复

几乎每个页面都重复实现：
- 状态标签映射函数（`riskLevelTagType`、`statusTagType`）
- 分页列表模式（`loading` + `tableData` + `pagination` + `loadData()`）
- 搜索表单 + 重置逻辑

#### 3. API 封装与使用脱节

`api/modules.ts` 定义了封装好的 API，但各页面直接使用 `request.get('/hard-coded/path')`，路径硬编码且不统一。

#### 4. 401 跳转使用整页刷新

```typescript
window.location.href = '/login'
```

SPA 应用应使用 `router.push('/login')` 避免整页刷新。

---

## 六、安全综合评估

| 维度 | 评分 | 说明 |
|------|------|------|
| 认证 | ⚠️ 中 | JWT + BCrypt 基本合理，但密钥硬编码 |
| 授权 | ❌ 差 | 后端有 RBAC 设计，但前端完全无权限控制 |
| 传输安全 | ⚠️ 中 | 依赖 HTTPS，密码明文传输 |
| 敏感数据保护 | ⚠️ 中 | AES 加密 + 脱敏 AOP，但密钥管理不当 |
| 审计日志 | ✅ 良 | AOP 自动记录操作日志 |
| XSS | ⚠️ 中 | 未使用 `v-html`，但无显式过滤器 |
| CSRF | ⚠️ 中 | 使用 Bearer Token 风险较低，但 CORS 配置危险 |
| SQL 注入 | ✅ 良 | 主要使用 MyBatis-Plus LambdaWrapper |

---

## 七、修复优先级建议

### 🔴 P0 - 必须立即修复

1. **移除所有硬编码密码/密钥**，改用环境变量或密钥管理系统
2. **修复 CORS 配置**，生产环境限制具体域名，禁止 `*` + `allowCredentials`
3. **添加前端权限控制**，基于角色的路由守卫、菜单过滤、按钮权限
4. **修复 ScreeningService N+1 查询**，使用批量查询 + 内存组装
5. **修复 ECharts 内存泄漏**，`onUnmounted` 中 `dispose()` + `removeEventListener`
6. **明确异步方法事务策略**，确保核心写操作在事务保护下

### 🟡 P1 - 建议尽快修复

7. **补充核心模块单元测试**（认证、交易、预警、案件、规则引擎）
8. **引入 TypeScript 类型定义**，消除 `any`
9. **提取公共组件/hooks**（`StatusTag`、`PageTable`、`usePagination`）
10. **统一构造器注入**，替换字段注入
11. **添加缓存注解**（客户详情、字典、名单数据）
12. **评估 Token 存储方案**（httpOnly Cookie）

### 🟢 P2 - 优化项

13. **引入 MapStruct** 消除 `BeanUtils.copyProperties` 重复代码
14. **状态枚举统一**，替换 Magic String
15. **URL 参数编码**，防止路径遍历
16. **图标按需引入**，减少打包体积
17. **添加 404 路由**和全局错误页面

---

## 八、总体评价

该系统在**技术选型、架构设计和基础设施**方面表现良好，采用了业界主流的技术栈和合理的分层架构。数据库设计规范，覆盖了反洗钱业务的完整流程。

但在**代码质量、安全合规、测试覆盖**方面存在明显不足：
- 后端存在硬编码密钥、CORS 配置危险、N+1 性能问题、事务边界不清等风险
- 前端存在严重的权限控制缺失、TypeScript 类型形同虚设、代码大量重复等问题

**作为金融合规类系统（反洗钱），当前代码状态不建议直接投产使用**。建议优先完成 P0 级别的安全修复和权限控制，补充核心流程的自动化测试，再进行安全渗透测试和代码审计。
