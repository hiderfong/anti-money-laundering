# STR/CTR Reporting State-Machine Unit Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Mockito unit tests covering the two regulatory submission state machines (`StrReportServiceImpl`, `LargeTxnReportServiceImpl`) — every guard, transition, and collaborator side effect.

**Architecture:** Two `@ExtendWith(MockitoExtension.class)` test classes mocking each service's mappers/collaborators, asserting status transitions (via the mutated fetched entity) and `BusinessException` guards. These are characterization tests of existing, correct code — they should pass on first run; a RED result means a discovered behavior mismatch to investigate per the spec's bug-handling rule (fix clear compliance holes + test; list minor ones).

**Tech Stack:** JUnit 5, Mockito (`@Mock`/`@InjectMocks`/`ArgumentCaptor`), matching `ScreeningServiceImplTest`/`CaseServiceImplTest` conventions. No Spring context.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `src/test/java/com/insurance/aml/module/casemgmt/service/StrReportServiceImplTest.java` (create) | STR state machine: create/submitForReview/review/submitToRegulator/getDetail guards + transitions |
| `src/test/java/com/insurance/aml/module/reporting/service/LargeTxnReportServiceImplTest.java` (create) | CTR state machine: generate/review/generateXml/submit/retry guards + transitions |

**Ground truth (verified against source):**
- `ReportStatus.getCode()` returns the enum name string: `DRAFT`, `PENDING_REVIEW`, `REVIEWED`, `APPROVED`, `REJECTED`, `SUBMITTED`.
- `SubmitStatus.getCode()`: `SUCCESS`, `FAILED`, `PENDING`. `CaseStatus.PENDING_APPROVAL.getCode()` = `"PENDING_APPROVAL"`.
- `SecurityUtils.getCurrentUserId()` / `getCurrentUsername()` return `null` when no security context (no static mock needed).
- `StrReportCreateRequest`: `caseId, reportType, reportContent, analysisOpinion, measuresTaken`. `StrReportReviewRequest`: `reportId, approved (Boolean), opinion`.
- `strReportMapper.selectCount(wrapper)` returns `Long`.
- Services mutate the fetched entity in place then call `updateById(report)`, so tests assert on the same fixture object after invocation.

---

### Task 1: StrReportServiceImplTest

**Files:**
- Create: `src/test/java/com/insurance/aml/module/casemgmt/service/StrReportServiceImplTest.java`

- [ ] **Step 1: Write the test class**

Create the file with exactly this content:

```java
package com.insurance.aml.module.casemgmt.service;

import com.insurance.aml.common.enums.CaseStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.casemgmt.mapper.CaseMapper;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.casemgmt.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.casemgmt.service.impl.StrReportServiceImpl;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.XmlGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STR（可疑交易报告）服务单元测试。
 * 覆盖 create→submitForReview→review→submitToRegulator 状态机的守卫与转换。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("STR可疑交易报告服务测试")
class StrReportServiceImplTest {

    @Mock StrReportMapper strReportMapper;
    @Mock CaseMapper caseMapper;
    @Mock CustomerMapper customerMapper;
    @Mock IdGenerator idGenerator;
    @Mock XmlGeneratorService xmlGeneratorService;
    @Mock ReportSubmitLogMapper reportSubmitLogMapper;
    @Mock CaseService caseService;

    @InjectMocks StrReportServiceImpl service;

    private StrReport reportWithStatus(String status) {
        StrReport r = new StrReport();
        r.setId(100L);
        r.setReportNo("STR-100");
        r.setCaseId(7L);
        r.setCustomerId(9L);
        r.setReportStatus(status);
        return r;
    }

    // ---- createReport ----

    @Test
    @DisplayName("创建报告-案件不存在-抛异常且不落库")
    void createReport_caseNotFound_throws() {
        when(caseMapper.selectById(7L)).thenReturn(null);
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);

        assertThrows(BusinessException.class, () -> service.createReport(req));
        verify(strReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建报告-同案件已有报告-抛异常")
    void createReport_duplicateForCase_throws() {
        Case c = new Case();
        c.setId(7L);
        c.setCustomerId(9L);
        when(caseMapper.selectById(7L)).thenReturn(c);
        when(strReportMapper.selectCount(any())).thenReturn(1L);
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);

        assertThrows(BusinessException.class, () -> service.createReport(req));
        verify(strReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建报告-正常-DRAFT状态且customerId取自案件")
    void createReport_happyPath_draft() {
        Case c = new Case();
        c.setId(7L);
        c.setCustomerId(9L);
        when(caseMapper.selectById(7L)).thenReturn(c);
        when(strReportMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.generateReportNo()).thenReturn("STR-NEW");
        StrReportCreateRequest req = new StrReportCreateRequest();
        req.setCaseId(7L);
        req.setReportType("SUSPICIOUS");

        StrReport result = service.createReport(req);

        assertEquals(ReportStatus.DRAFT.getCode(), result.getReportStatus());
        assertEquals(9L, result.getCustomerId());
        assertEquals("STR-NEW", result.getReportNo());
        verify(strReportMapper, times(1)).insert(any());
    }

    // ---- submitForReview ----

    @Test
    @DisplayName("提交审核-报告不存在-抛异常")
    void submitForReview_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitForReview(100L));
    }

    @Test
    @DisplayName("提交审核-非DRAFT状态-抛异常")
    void submitForReview_nonDraft_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.APPROVED.getCode()));
        assertThrows(BusinessException.class, () -> service.submitForReview(100L));
        verify(strReportMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("提交审核-DRAFT-转PENDING_REVIEW")
    void submitForReview_draft_transitionsToPendingReview() {
        StrReport r = reportWithStatus(ReportStatus.DRAFT.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);

        service.submitForReview(100L);

        assertEquals(ReportStatus.PENDING_REVIEW.getCode(), r.getReportStatus());
        verify(strReportMapper, times(1)).updateById(r);
    }

    // ---- reviewReport ----

    @Test
    @DisplayName("审核-报告不存在-抛异常")
    void reviewReport_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        assertThrows(BusinessException.class, () -> service.reviewReport(req));
    }

    @Test
    @DisplayName("审核-非PENDING_REVIEW状态-抛异常")
    void reviewReport_nonPending_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.DRAFT.getCode()));
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        assertThrows(BusinessException.class, () -> service.reviewReport(req));
        verify(caseService, never()).changeCaseStatus(any(), any(), any());
    }

    @Test
    @DisplayName("审核-批准-转APPROVED并推进案件到PENDING_APPROVAL")
    void reviewReport_approved_advancesCase() {
        StrReport r = reportWithStatus(ReportStatus.PENDING_REVIEW.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.TRUE);
        req.setOpinion("通过");

        service.reviewReport(req);

        assertEquals(ReportStatus.APPROVED.getCode(), r.getReportStatus());
        verify(caseService, times(1)).changeCaseStatus(eq(7L),
                eq(CaseStatus.PENDING_APPROVAL.getCode()), anyString());
    }

    @Test
    @DisplayName("审核-拒绝-转REJECTED且不动案件")
    void reviewReport_rejected_doesNotTouchCase() {
        StrReport r = reportWithStatus(ReportStatus.PENDING_REVIEW.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        StrReportReviewRequest req = new StrReportReviewRequest();
        req.setReportId(100L);
        req.setApproved(Boolean.FALSE);
        req.setOpinion("驳回");

        service.reviewReport(req);

        assertEquals(ReportStatus.REJECTED.getCode(), r.getReportStatus());
        verify(caseService, never()).changeCaseStatus(any(), any(), any());
    }

    // ---- submitToRegulator ----

    @Test
    @DisplayName("提交监管-报告不存在-抛异常")
    void submitToRegulator_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitToRegulator(100L));
    }

    @Test
    @DisplayName("提交监管-非APPROVED状态-拒绝(合规关键)")
    void submitToRegulator_nonApproved_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(reportWithStatus(ReportStatus.PENDING_REVIEW.getCode()));
        assertThrows(BusinessException.class, () -> service.submitToRegulator(100L));
        verify(reportSubmitLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("提交监管-APPROVED-转SUBMITTED并生成XML写成功日志")
    void submitToRegulator_approved_submitsAndLogs() {
        StrReport r = reportWithStatus(ReportStatus.APPROVED.getCode());
        when(strReportMapper.selectById(100L)).thenReturn(r);
        when(xmlGeneratorService.generateSuspiciousTxnXml(r)).thenReturn("<str/>");

        service.submitToRegulator(100L);

        assertEquals(ReportStatus.SUBMITTED.getCode(), r.getReportStatus());
        verify(xmlGeneratorService, times(1)).generateSuspiciousTxnXml(r);
        ArgumentCaptor<ReportSubmitLog> captor = ArgumentCaptor.forClass(ReportSubmitLog.class);
        verify(reportSubmitLogMapper, times(1)).insert(captor.capture());
        assertEquals("SUSPICIOUS", captor.getValue().getReportType());
        assertEquals("SUCCESS", captor.getValue().getSubmitStatus());
    }

    // ---- getReportDetail ----

    @Test
    @DisplayName("查询详情-报告不存在-抛异常")
    void getReportDetail_notFound_throws() {
        when(strReportMapper.selectById(100L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getReportDetail(100L));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=StrReportServiceImplTest`
Read `target/surefire-reports/com.insurance.aml.module.casemgmt.service.StrReportServiceImplTest.txt`. Expected: `Tests run: 14, Failures: 0, Errors: 0`.

If any test is RED: it is a discovered behavior mismatch. First confirm the test's expectation matches the impl's actual code path (re-read the impl). If the test is wrong, fix the test to characterize actual behavior. If the impl has a clear compliance hole (e.g., a guard missing), STOP and report it per the spec bug-handling rule — do not silently change production logic without flagging.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/insurance/aml/module/casemgmt/service/StrReportServiceImplTest.java
git commit -m "test: cover STR report state machine (create/review/submit guards)"
```

---

### Task 2: LargeTxnReportServiceImplTest

**Files:**
- Create: `src/test/java/com/insurance/aml/module/reporting/service/LargeTxnReportServiceImplTest.java`

- [ ] **Step 1: Write the test class**

Create the file with exactly this content:

```java
package com.insurance.aml.module.reporting.service;

import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.SubmitStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.service.impl.LargeTxnReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CTR（大额交易报告）服务单元测试。
 * 覆盖 generate→review→submit 状态机与 generateXml / retry 守卫。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CTR大额交易报告服务测试")
class LargeTxnReportServiceImplTest {

    @Mock LargeTxnReportMapper largeTxnReportMapper;
    @Mock ReportSubmitLogMapper reportSubmitLogMapper;
    @Mock TransactionMapper transactionMapper;
    @Mock CustomerMapper customerMapper;
    @Mock IdGenerator idGenerator;
    @Mock XmlGeneratorService xmlGeneratorService;

    @InjectMocks LargeTxnReportServiceImpl service;

    private Transaction txn() {
        Transaction t = new Transaction();
        t.setId(5L);
        t.setCustomerId(9L);
        return t;
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setId(9L);
        c.setName("测试客户");
        return c;
    }

    private LargeTxnReport reportWithStatus(String status) {
        LargeTxnReport r = new LargeTxnReport();
        r.setId(200L);
        r.setReportNo("CTR-200");
        r.setTransactionId(5L);
        r.setCustomerId(9L);
        r.setReportStatus(status);
        return r;
    }

    // ---- generateReport ----

    @Test
    @DisplayName("生成报告-交易不存在-抛异常")
    void generateReport_transactionNotFound_throws() {
        when(transactionMapper.selectById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateReport(5L));
        verify(largeTxnReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("生成报告-客户不存在-抛异常")
    void generateReport_customerNotFound_throws() {
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateReport(5L));
        verify(largeTxnReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("生成报告-正常-DRAFT状态")
    void generateReport_happyPath_draft() {
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(idGenerator.generateReportNo()).thenReturn("CTR-NEW");

        LargeTxnReport result = service.generateReport(5L);

        assertEquals(ReportStatus.DRAFT.getCode(), result.getReportStatus());
        assertEquals(9L, result.getCustomerId());
        assertEquals("CTR-NEW", result.getReportNo());
        verify(largeTxnReportMapper, times(1)).insert(any());
    }

    // ---- reviewReport ----

    @Test
    @DisplayName("审核-报告不存在-抛异常")
    void reviewReport_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reviewReport(200L, "reviewer"));
    }

    @Test
    @DisplayName("审核-非DRAFT状态-抛异常")
    void reviewReport_nonDraft_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.SUBMITTED.getCode()));
        assertThrows(BusinessException.class, () -> service.reviewReport(200L, "reviewer"));
        verify(largeTxnReportMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("审核-DRAFT-转REVIEWED并记录审核人")
    void reviewReport_draft_transitionsToReviewed() {
        LargeTxnReport r = reportWithStatus(ReportStatus.DRAFT.getCode());
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);

        service.reviewReport(200L, "reviewer-a");

        assertEquals(ReportStatus.REVIEWED.getCode(), r.getReportStatus());
        assertEquals("reviewer-a", r.getReviewedBy());
        verify(largeTxnReportMapper, times(1)).updateById(r);
    }

    // ---- generateXml ----

    @Test
    @DisplayName("生成XML-报告不存在-抛异常")
    void generateXml_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateXml(200L));
    }

    @Test
    @DisplayName("生成XML-关联交易或客户缺失-抛异常")
    void generateXml_missingTransactionOrCustomer_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.REVIEWED.getCode()));
        when(transactionMapper.selectById(5L)).thenReturn(null);
        when(customerMapper.selectById(9L)).thenReturn(customer());
        assertThrows(BusinessException.class, () -> service.generateXml(200L));
    }

    @Test
    @DisplayName("生成XML-正常-委派XmlGeneratorService")
    void generateXml_happyPath_delegates() {
        LargeTxnReport r = reportWithStatus(ReportStatus.REVIEWED.getCode());
        Transaction t = txn();
        Customer c = customer();
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);
        when(transactionMapper.selectById(5L)).thenReturn(t);
        when(customerMapper.selectById(9L)).thenReturn(c);
        when(xmlGeneratorService.generateLargeTxnXml(r, c, t)).thenReturn("<ctr/>");

        String xml = service.generateXml(200L);

        assertEquals("<ctr/>", xml);
        verify(xmlGeneratorService, times(1)).generateLargeTxnXml(r, c, t);
    }

    // ---- submitReport ----

    @Test
    @DisplayName("提交-报告不存在-抛异常")
    void submitReport_notFound_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitReport(200L));
    }

    @Test
    @DisplayName("提交-非REVIEWED状态-拒绝(合规关键)")
    void submitReport_nonReviewed_throws() {
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.DRAFT.getCode()));
        assertThrows(BusinessException.class, () -> service.submitReport(200L));
        verify(reportSubmitLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("提交-REVIEWED-转SUBMITTED并写成功日志")
    void submitReport_reviewed_submitsAndLogs() {
        LargeTxnReport r = reportWithStatus(ReportStatus.REVIEWED.getCode());
        when(largeTxnReportMapper.selectById(200L)).thenReturn(r);
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(xmlGeneratorService.generateLargeTxnXml(any(), any(), any())).thenReturn("<ctr/>");

        service.submitReport(200L);

        assertEquals(ReportStatus.SUBMITTED.getCode(), r.getReportStatus());
        verify(reportSubmitLogMapper, times(1)).insert(any());
    }

    // ---- retryFailedSubmissions ----

    @Test
    @DisplayName("重试-上限内失败日志-重试成功并标记SUCCESS")
    void retryFailedSubmissions_retriesWithinLimit() {
        ReportSubmitLog failed = new ReportSubmitLog();
        failed.setReportId(200L);
        failed.setSubmitStatus(SubmitStatus.FAILED.getCode());
        failed.setRetryCount(0);
        failed.setMaxRetries(3);
        when(reportSubmitLogMapper.selectList(any())).thenReturn(List.of(failed));
        // submitReport(200L) 内部链路：报告 REVIEWED + 交易/客户存在 + XML 生成
        when(largeTxnReportMapper.selectById(200L)).thenReturn(reportWithStatus(ReportStatus.REVIEWED.getCode()));
        when(transactionMapper.selectById(5L)).thenReturn(txn());
        when(customerMapper.selectById(9L)).thenReturn(customer());
        when(xmlGeneratorService.generateLargeTxnXml(any(), any(), any())).thenReturn("<ctr/>");

        service.retryFailedSubmissions();

        assertEquals(SubmitStatus.SUCCESS.getCode(), failed.getSubmitStatus());
        assertEquals(1, failed.getRetryCount());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -q test -Dtest=LargeTxnReportServiceImplTest`
Read `target/surefire-reports/com.insurance.aml.module.reporting.service.LargeTxnReportServiceImplTest.txt`. Expected: `Tests run: 13, Failures: 0, Errors: 0`.

Same RED-handling rule as Task 1: a failing test is a discovered behavior mismatch — confirm the impl's actual path, fix the test if the expectation was wrong, or STOP and report a genuine compliance hole per the spec.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/insurance/aml/module/reporting/service/LargeTxnReportServiceImplTest.java
git commit -m "test: cover CTR large-txn report state machine (review/submit/retry guards)"
```

---

### Task 3: Full regression + finalize

- [ ] **Step 1: Full suite**

```bash
mvn -q test > /dev/null 2>&1; echo "exit=$?"
```
Expected: `exit=0`.

- [ ] **Step 2: Aggregate count (confirm +27, no regression)**

```bash
grep -h 'Tests run' target/surefire-reports/*.txt | awk -F'[, ]+' '{run+=$3; fail+=$5; err+=$7} END {print "run="run" fail="fail" err="err}'
```
Expected: `run=267 fail=0 err=0` (240 baseline + 14 STR + 13 CTR).

- [ ] **Step 3: Merge to main and push** (matches established session flow)

```bash
git checkout main
git merge --ff-only test/str-ctr-reporting-coverage
git branch -d test/str-ctr-reporting-coverage
git push github main
```

- [ ] **Step 4: Report** spec §6 acceptance coverage + whether any test surfaced a behavior mismatch (and how it was handled).

---

## Self-Review

**Spec coverage:**
- §2 state machines → Task 1 (STR) + Task 2 (CTR) ✓.
- §3 test components (2 classes, mock lists) → file structure + Task 1/2 mock fields match the impls' `private final` deps exactly ✓.
- §4 case list: STR 14 cases all present (createReport ×3, submitForReview ×3, reviewReport ×4, submitToRegulator ×3, getReportDetail ×1); CTR 13 cases all present (generateReport ×3, reviewReport ×3, generateXml ×3, submitReport ×3, retry ×1) ✓.
- §4 compliance-critical cases (`submitToRegulator_nonApproved`, `submitReport_nonReviewed`) present ✓.
- §4 collaborator side effects: `caseService.changeCaseStatus` verify, `reportSubmitLogMapper.insert` captor on reportType+submitStatus, `xmlGeneratorService` verify ✓.
- §5 SecurityUtils: no static mock (returns null safely) — fixtures don't depend on user id ✓.
- §6 acceptance 1–6: tests present, guards asserted, compliance cases present, side-effects verified, full regression, no prod change unless bug found ✓.

**Placeholder scan:** none. All test bodies complete with real assertions.

**Type consistency:** `reportWithStatus(String)` helper consistent within each class. `ReportStatus.*.getCode()` / `SubmitStatus.*.getCode()` / `CaseStatus.PENDING_APPROVAL.getCode()` used uniformly. Mock field types match each impl's constructor deps (STR: 7 deps; CTR: 6 deps). `StrReportReviewRequest` fields (reportId/approved/opinion) and `StrReportCreateRequest` fields (caseId/reportType) match verified source. `largeTxnReportMapper.selectById` returns `LargeTxnReport`; `strReportMapper.selectCount` returns `Long` (stubbed `0L`/`1L`).

No gaps found.
