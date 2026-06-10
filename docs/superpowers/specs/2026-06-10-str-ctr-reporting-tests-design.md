# P1-B（本轮）— STR/CTR 监管报送状态机单元测试

> 日期：2026-06-10
> 代码基线：`7d932aa` (main)
> 关联：`docs/development/全量分析报告-20260530.md`（P1-B 项）
> 状态：设计已确认，待 spec review → writing-plans

---

## 1. 背景与目标

全量分析报告 P1-B 指出：监管报送链路（STR/CTR）及 assessment/prevention/rectification 模块零单元测试。对一个反洗钱合规系统，**对外监管提交路径**（可疑交易报告 STR、大额交易报告 CTR）是最受监管关注、回归风险最高的逻辑，却缺少任何回归保护。

**本轮目标**：为两个监管报送状态机服务补 Mockito 单元测试，锁定其状态转换与守卫行为，建立回归安全网。

**已确认的范围决策**
- 范围：仅 STR + CTR 两个报送服务（`StrReportServiceImpl` + `LargeTxnReportServiceImpl`）；assessment/prevention/rectification 推迟后续轮次。
- 形式：纯 Mockito 单元测试（与 `ScreeningServiceImplTest`/`CaseServiceImplTest`/`AlertServiceImplTest` 一致），不引入 Spring 上下文。
- bug 处理：特征化为主 + 分级修复——测试锁定当前行为；发现「明显合规漏洞」当场修复并加测试，「轻微/可商榷」列出待用户拍板。

**静态阅读结论**：两个服务均为清晰状态机，守卫正确——`submitToRegulator` 强制 `APPROVED`、CTR `submitReport` 强制 `REVIEWED`，**未发现"未复核报告可达监管"类合规漏洞**。故本轮预期是纯安全网；若测试暴露意外边界行为，按 bug 处理决策特征化并分级上报。

---

## 2. 被测状态机

### STR（可疑交易报告，`StrReportServiceImpl`，339 行）
```
createReport ──▶ DRAFT
                  │ submitForReview (仅 DRAFT)
                  ▼
              PENDING_REVIEW
                  │ reviewReport
        approved │ │ rejected
                  ▼ ▼
            APPROVED  REJECTED
                  │ submitToRegulator (仅 APPROVED)
                  ▼
              SUBMITTED  (+ 生成 XML + 写 SUCCESS 提交日志 + 触发案件状态流转)
```
守卫：每个转换前校验实体存在与状态合法，否则 `BusinessException`。reviewReport 批准时额外调用 `caseService.changeCaseStatus(caseId, PENDING_APPROVAL, ...)`。

### CTR（大额交易报告，`LargeTxnReportServiceImpl`，298 行）
```
generateReport(txnId) ──▶ DRAFT  (校验交易+客户存在)
                            │ reviewReport (仅 DRAFT)
                            ▼
                        REVIEWED
                            │ submitReport (仅 REVIEWED)
                            ▼
                        SUBMITTED  (+ generateXml + 写 SUCCESS 提交日志)
retryFailedSubmissions：扫描 FAILED 且 retryCount<3 的日志，递增 retryCount 后重试 submitReport，
                        成功标 SUCCESS，异常存 errorMessage（日志保持 FAILED）。
```

---

## 3. 测试组件

| 测试类 | 文件 | mock 依赖 |
|--------|------|-----------|
| `StrReportServiceImplTest` | `src/test/java/com/insurance/aml/module/casemgmt/service/StrReportServiceImplTest.java` | `StrReportMapper`, `CaseMapper`, `CustomerMapper`, `IdGenerator`, `XmlGeneratorService`, `ReportSubmitLogMapper`, `CaseService` |
| `LargeTxnReportServiceImplTest` | `src/test/java/com/insurance/aml/module/reporting/service/LargeTxnReportServiceImplTest.java` | `LargeTxnReportMapper`, `ReportSubmitLogMapper`, `TransactionMapper`, `CustomerMapper`, `IdGenerator`, `XmlGeneratorService` |

`@ExtendWith(MockitoExtension.class)` + `@InjectMocks`，对齐既有服务测试。`@MockitoSettings(strictness = LENIENT)` 仅在需要时使用。

---

## 4. 用例清单

### StrReportServiceImplTest（~14）
1. `createReport_caseNotFound_throws` — caseMapper 返回 null → `BusinessException`，不 insert
2. `createReport_duplicateForCase_throws` — `selectCount` > 0 → `BusinessException`
3. `createReport_happyPath` — 正常 → reportStatus=DRAFT、customerId 取自案件、reportNo 取自 idGenerator、`insert` 被调用
4. `submitForReview_notFound_throws`
5. `submitForReview_nonDraft_throws` — 非 DRAFT 状态 → 异常
6. `submitForReview_draft_transitionsToPendingReview` — DRAFT → PENDING_REVIEW，`updateById` 被调用
7. `reviewReport_notFound_throws`
8. `reviewReport_nonPending_throws` — 非 PENDING_REVIEW → 异常
9. `reviewReport_approved_setsApprovedAndAdvancesCase` — APPROVED + `caseService.changeCaseStatus(caseId, PENDING_APPROVAL, ...)` 被调用一次
10. `reviewReport_rejected_setsRejectedAndDoesNotTouchCase` — REJECTED + `caseService` 零调用
11. `submitToRegulator_notFound_throws`
12. `submitToRegulator_nonApproved_throws` — **合规关键**：非 APPROVED 不可提交
13. `submitToRegulator_approved_submitsAndLogs` — SUBMITTED + `xmlGeneratorService.generateSuspiciousTxnXml` 被调用 + `reportSubmitLogMapper.insert` 写入 SUCCESS、reportType=SUSPICIOUS
14. `getReportDetail_notFound_throws`

### LargeTxnReportServiceImplTest（~13）
1. `generateReport_transactionNotFound_throws` — `ResultCode.NOT_FOUND`
2. `generateReport_customerNotFound_throws`
3. `generateReport_happyPath` — DRAFT、金额/币种/交易时间等字段取自交易、`insert` 被调用
4. `reviewReport_notFound_throws`
5. `reviewReport_nonDraft_throws`
6. `reviewReport_draft_transitionsToReviewed` — REVIEWED + reviewedBy 设置
7. `generateXml_notFound_throws`
8. `generateXml_missingTransactionOrCustomer_throws` — 交易或客户为 null → 异常
9. `generateXml_happyPath_delegatesToGenerator` — 委派 `xmlGeneratorService.generateLargeTxnXml`
10. `submitReport_notFound_throws`
11. `submitReport_nonReviewed_throws` — **合规关键**：非 REVIEWED 不可提交
12. `submitReport_reviewed_submitsAndLogs` — SUBMITTED + xml + `reportSubmitLogMapper.insert` 写 SUCCESS、reportType=LARGE_TXN
13. `retryFailedSubmissions_retriesFailedWithinLimit` — 一条 FAILED 且 retryCount<3 的日志 → retryCount 递增、重试 `submitReport`、成功后日志标 SUCCESS

---

## 5. 边界与实现注意

- **`SecurityUtils` 静态调用**：`getCurrentUserId()`（STR）与 `getCurrentUsername()`（CTR）在无安全上下文时返回 null，服务有兜底（writerId/reviewerId 可空、submittedBy→"system"）。优先不 mock；若某用例因 null 触发异常，再用 `mockStatic(SecurityUtils.class)`。
- **`retryFailedSubmissions` 自调用**：内部直接调 `submitReport(...)`（非 `this.` 代理），单测 mock 下不涉及事务代理，断言行为即可。
- **状态码来源**：断言用 `ReportStatus.*.getCode()` / `SubmitStatus.*.getCode()` 枚举，不硬编码裸字符串。
- **PageResult 查询方法**（pageQuery/pageQueryReports）：以 mapper `selectPage` 返回构造 `Page`，可加 1 条轻量用例验证 wrapper 状态过滤与映射；非守卫核心，列为可选。

---

## 6. 验收标准

1. 两个测试类落地，覆盖 §4 全部用例（STR ~14、CTR ~13）。
2. 每条状态守卫（实体不存在、状态非法）均有断言 `BusinessException` 的用例。
3. 两条监管提交路径的"非法前置状态被拒绝"用例存在（合规关键）。
4. 协作副作用（XML 生成、提交日志写入、案件状态流转）经 `verify` 断言。
5. 新增用例全绿；全量 `mvn test` 保持绿（当前基线 240/0/0，预期 ≈267）。
6. 不改动 STR/CTR 生产逻辑——除非测试暴露明显合规漏洞（按 bug 处理决策当场修复+加测试并在提交说明，轻微项列出上报）。
