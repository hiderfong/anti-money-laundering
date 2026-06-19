# P1-B续 — 合规模块（自评估/整改/特别防控）关键路径单测

> 日期：2026-06-19
> 代码基线：`d9c5cd7` (main)
> 关联：`docs/development/全量分析报告-20260616.md`（P1-B续，行 50/76）；延续 P1-B STR/CTR 报送测试
> 状态：设计已确认（覆盖深度＝关键合规路径），待 spec review → writing-plans

---

## 1. 背景与目标

P1-B 已覆盖对外报送（STR/CTR）状态机。全量报告指出**内部合规链路仍零单测**：assessment（`SelfAssessmentServiceImpl` 298、`RectificationServiceImpl` 237）、prevention（`SpecialPreventionServiceImpl` 454）。这三块承载风险自评估打分、整改任务闭环、特别防控（名单更新/回溯筛查/查冻扣/筛查升级建案）等合规关键逻辑，缺回归保护。

**目标**：为三个服务补**关键合规路径**单元测试，断言代码文档化的应然行为（状态门禁、阈值计算、默认值、异常守卫）。纯新增测试，**不改任何生产代码**。

**覆盖深度（已确认）**：关键合规路径 —— 状态机门禁、评分/风险等级/优先级阈值、默认值与遗留名归一、not-found 守卫、逾期检测；跳过纯透传的 page/list 查询。约 40-46 用例（§3 清单为准）。

---

## 2. 测试架构与约定

3 个新测试文件，严格沿用现有约定（对照 `CaseServiceImplTest`、`LargeTxnReportServiceImplTest`）：

- `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`
- `@Mock` 注入各 Mapper/Service，`@InjectMocks` 被测服务
- 静态方法（`SecurityUtils.getCurrentUsername()`）用 `MockedStatic`（try-with-resources 作用域）
- 零 Spring 上下文、零 DB；纯 Mockito 行为驱动 + 参数捕获（`ArgumentCaptor`）断言落库字段

| 测试文件 | 被测 | Mock 依赖 |
|----------|------|-----------|
| `src/test/java/com/insurance/aml/module/assessment/service/SelfAssessmentServiceImplTest.java` | `SelfAssessmentServiceImpl` | `SelfAssessmentMapper`、`AssessmentIndicatorMapper`、`AssessmentScoreMapper` |
| `src/test/java/com/insurance/aml/module/assessment/service/RectificationServiceImplTest.java` | `RectificationServiceImpl` | `RectificationTaskMapper` + 静态 `SecurityUtils` |
| `src/test/java/com/insurance/aml/module/prevention/service/SpecialPreventionServiceImplTest.java` | `SpecialPreventionServiceImpl` | `WatchlistUpdateJobMapper`、`RetrospectiveScreeningJobMapper`、`SpecialMeasureMapper`、`FreezeSeizureDeductionMapper`、`WatchlistSourceMapper`、`WatchlistMapper`、`ScreeningResultMapper`、`CustomerMapper`、`AlertService`、`AlertMapper`、`CaseService`、`IdGenerator` + 静态 `SecurityUtils` |

---

## 3. 覆盖清单（关键合规路径）

### 3.1 SelfAssessmentServiceImpl（15）

| 方法 | 用例 | 断言 |
|------|------|------|
| `submitScore` | 评估不存在 | 抛 `RuntimeException` |
| | 状态非 CREATED/IN_PROGRESS（如 COMPLETED） | 抛异常，不落分 |
| | CREATED → 自动转 IN_PROGRESS | `assessmentMapper.updateById` 入参 status="IN_PROGRESS" |
| | 指标不存在 | 抛异常 |
| | 已有该指标评分 | 走 `scoreMapper.updateById`（非 insert） |
| | 无既有评分 | 走 `scoreMapper.insert` |
| `completeAssessment` | 状态非 IN_PROGRESS | 抛异常 |
| | 无任何评分 | 抛异常 |
| | 加权计算 + 等级 HIGH（overall>70） | inherent×0.6+control×0.4；overallRiskLevel=HIGH |
| | 等级 MEDIUM（40≤overall≤70） | overallRiskLevel=MEDIUM |
| | 等级 LOW（overall<40） | overallRiskLevel=LOW |
| `approveAssessment` | 状态非 COMPLETED | 抛异常 |
| | 成功 | status=APPROVED，approvedBy/approvedTime 落库 |
| `getAssessmentDetail` | 不存在 | 抛异常 |
| | 含评分 | VO 装配指标明细（code/name/category/weight） |

> 评分阈值口径取自源码：`overallScore = inherent*0.6 + control*0.4`（int 截断）；`>70 HIGH / 40-70 MEDIUM / <40 LOW`；加权平均按 `HALF_UP` 取整。指标按 `category`（INHERENT_RISK / CONTROL_EFFECTIVENESS）分组、`weight` 加权。测试构造启用指标 + 评分使各档边界可判定。

### 3.2 RectificationServiceImpl（16）

| 方法 | 用例 | 断言 |
|------|------|------|
| `createTask` | 默认值 | status=OPEN、progressPercent=0、verificationStatus=PENDING、sourceType 缺省→SELF_ASSESSMENT |
| | 遗留姓名归一 | responsiblePerson="admin"→"刘思远" |
| | 显式 sourceType | 保留入参 sourceType |
| `updateTaskStatus` | 任务不存在 | 抛异常 |
| | → COMPLETED | completedTime 置时、progressPercent=100 |
| | → VERIFIED | closedTime 置时、verificationStatus=PASSED |
| `updateProgress` | 不存在 | 抛异常 |
| | progress≥100 | status=COMPLETED、completedTime 置时 |
| | 0<progress<100 | status=IN_PROGRESS |
| `verifyTask(id, verifiedBy)` | 状态非 COMPLETED（合规门禁） | 抛异常 |
| | 成功 | status=VERIFIED、verificationStatus=PASSED、verifiedBy 归一、closedTime 置时 |
| `verifyTask(id, req)` | 状态非 COMPLETED/VERIFIED | 抛异常 |
| | PASSED | status=VERIFIED、closedTime 置时 |
| | RETURNED | status=IN_PROGRESS、closedTime=null |
| `listTasks` | 逾期检测：OPEN/IN_PROGRESS 且 deadline 已过 | status→OVERDUE 且 `updateById` 落库 |
| | 未过期任务 | 不改状态 |

> `verifyTask(id, req)` 内调 `SecurityUtils.getCurrentUsername()` → 需 `MockedStatic`。

### 3.3 SpecialPreventionServiceImpl（15）

| 方法 | 用例 | 断言 |
|------|------|------|
| `createWatchlistUpdateJob` | sourceId 指定但名单源不存在 | 抛 `BusinessException`（NOT_FOUND） |
| | sourceId 为空 | sourceName="全部名单源"，无 source 更新副作用 |
| | sourceId 有效 | source.lastUpdateTime/totalEntries 更新落库 |
| `createRetrospectiveJob`（经 `countCustomersByScope`） | scope=CUSTOMER_IDS | totalCustomers=逗号分隔计数 |
| | scope=HIGH_RISK | `customerMapper.selectCount` 按 HIGH 过滤 |
| `createSpecialMeasure` | 客户不存在 | 抛 `BusinessException`（CUSTOMER_NOT_FOUND） |
| | controlLevel 缺省 | →MEDIUM |
| `updateSpecialMeasureStatus` | 不存在 | 抛 `BusinessException` |
| `createFreezeRecord` | 客户不存在 | 抛 `BusinessException` |
| | currency 缺省 | →CNY |
| `updateFreezeRecordStatus` | 不存在 | 抛 `BusinessException` |
| `escalateScreeningResultToAlert` | 结果不存在 | 抛 `BusinessException` |
| | 成功 | result.reviewStatus→ESCALATED 落库，调 `alertService.createAlert` |
| `createCaseFromScreeningResult` | matchScore≥95 | 风险等级 CRITICAL、案件 priority=5；走 escalate→alert CONFIRMED→`caseService.createCase` |
| | matchScore<95（或 null） | 风险等级 HIGH、案件 priority=4 |

> `escalateScreeningResultToAlert`/`createCaseFromScreeningResult` 内调 `SecurityUtils.getCurrentUsername()` → 需 `MockedStatic`。优先级/等级阈值（私有 `resolveCasePriority`/`resolveRiskLevel`）经公开方法间接覆盖。

---

## 4. 边界与一致性

- 异常类型按各模块实际：assessment/rectification 抛 `java.lang.RuntimeException`（断言类型 + 可选 message 关键字）；prevention 抛 `com.insurance.aml.common.exception.BusinessException`（断言 `getCode()` 对应 `ResultCode`）。
- 不动生产代码、不动控制器、不加 Spring 集成测试。
- 状态码取自枚举：`AssessmentStatusEnum`（CREATED/COMPLETED/APPROVED，IN_PROGRESS 在 service 内为字符串字面量）、`RectificationStatus`（OPEN/IN_PROGRESS/COMPLETED/OVERDUE/VERIFIED）；测试直接引用枚举 `getCode()` 保持耦合稳定。

---

## 5. 测试性质说明（重要）

被测三服务**代码已存在**，故本轮是**回归/特征化测试**，非 TDD 先红后绿：测试断言"代码文档化的应然行为"（注释与方法语义所述阈值/门禁/默认值）。

- 若某断言与现行实现一致 → 直接绿（建立回归基线）。
- 若某断言意外失败 → 说明实现与应然行为不符 = **暴露真实 bug**，作为发现上报，**不擅自修改生产代码**（超出本轮纯增量测试范围，单列）。

---

## 6. 验收标准

1. §3 三张表所列关键路径全部有对应 `@Test`，约 40-46 用例（SelfAssessment 15 + Rectification 16 + SpecialPrevention 15）。
2. 全量 `mvn test` 保持绿（当前 275；预期 ~315-321）。
3. 三个服务从零测试 → 关键合规路径覆盖（状态门禁、阈值计算、默认值、异常守卫、逾期检测）。
4. 生产代码零改动（`git diff` 仅 `src/test/**` 新增）。
5. 任何测试中发现的实现与应然行为偏差，记录为 backlog，本轮不修。

---

## 7. 非目标（本轮不做）

- `retryFailedSubmissions` 的 `lt(retryCount,3)` DB-filter 切片测试（需 `@SpringBootTest`，独立 follow-up）。
- 纯透传的分页/列表查询（`pageWatchlistUpdateJobs`/`pageSpecialMeasures`/`pageFreezeRecords`/`pageRetrospectiveJobs`/`listAssessments`/`pageTasks`）无业务逻辑，不强测（除 `listTasks` 含逾期检测、`pageTasks` 的 overdue 经 `listTasks` 间接覆盖）。
- 控制器层 RBAC（已由 P2-E 覆盖）。
- 前端测试（P1-C）。
- 任何生产代码修改（含发现的 bug 修复）。
