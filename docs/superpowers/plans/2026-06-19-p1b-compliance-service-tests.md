# P1-B续 合规模块关键路径单测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `SelfAssessmentServiceImpl`、`RectificationServiceImpl`、`SpecialPreventionServiceImpl` 三个零测试合规服务补关键路径回归单测（约 46 用例），不改任何生产代码。

**Architecture:** 纯 Mockito 单元测试（无 Spring 上下文、无 DB），`@Mock` 注入 Mapper/Service，`@InjectMocks` 被测服务，`MockedStatic` 处理静态 `SecurityUtils`，`ArgumentCaptor` 断言落库字段。每个测试文件一个 Task，独立编译、运行、提交。

**Tech Stack:** JUnit 5、Mockito（`mockito-junit-jupiter`、`mockito-inline`/`mockito-core` 静态 mock）、Maven Surefire。

**测试性质（重要）：** 被测代码已存在，故这是**回归/特征化测试**——断言代码文档化的应然行为；写完即应直接绿。若某断言失败 = 暴露真实 bug，**记录为发现上报，不修改生产代码**（超出本轮范围）。

**关键事实（已逐一核对源码）：**
- 异常：assessment/rectification 抛 `java.lang.RuntimeException`；prevention 抛 `com.insurance.aml.common.exception.BusinessException`（`getCode()` 为 int，`ResultCode.NOT_FOUND`=404、`CUSTOMER_NOT_FOUND`=110001）。
- 枚举：`AssessmentStatusEnum`(CREATED/COMPLETED/APPROVED)、`RectificationStatus`(OPEN/IN_PROGRESS/COMPLETED/OVERDUE/VERIFIED)、`RiskLevel`(LOW/MEDIUM/HIGH/CRITICAL)；`SelfAssessmentServiceImpl` 内部用字符串字面量 `"IN_PROGRESS"`。
- 综合评分：`overallScore = (int)(inherent*0.6 + control*0.4)`，加权均值 `HALF_UP` 取整；`>70 HIGH / ≥40 MEDIUM / else LOW`。
- 静态依赖：`RectificationServiceImpl.verifyTask(Long, RectificationVerifyRequest)` 与 `SpecialPreventionServiceImpl.escalateScreeningResultToAlert/createCaseFromScreeningResult` 调 `SecurityUtils.getCurrentUsername()`；`verifyTask(Long, String)` 不调（用入参）。
- 遗留姓名归一：`admin → 刘思远`、`system → 系统自动处理`。
- 现有约定：`@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`（见 `CaseServiceImplTest`）。`mockStatic` 已在 `CaseServiceImplTest` 中使用，依赖可用。

---

## File Structure

- Create: `src/test/java/com/insurance/aml/module/assessment/service/SelfAssessmentServiceImplTest.java`（Task 1，15 用例）
- Create: `src/test/java/com/insurance/aml/module/assessment/service/RectificationServiceImplTest.java`（Task 2，16 用例）
- Create: `src/test/java/com/insurance/aml/module/prevention/service/SpecialPreventionServiceImplTest.java`（Task 3，15 用例）

无生产代码修改。

---

## Task 1: SelfAssessmentServiceImpl 单测

**Files:**
- Create: `src/test/java/com/insurance/aml/module/assessment/service/SelfAssessmentServiceImplTest.java`

- [ ] **Step 1: 写测试文件（全部 15 用例）**

```java
package com.insurance.aml.module.assessment.service;

import com.insurance.aml.common.enums.AssessmentStatusEnum;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.module.assessment.mapper.AssessmentIndicatorMapper;
import com.insurance.aml.module.assessment.mapper.AssessmentScoreMapper;
import com.insurance.aml.module.assessment.mapper.SelfAssessmentMapper;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreRequest;
import com.insurance.aml.module.assessment.model.dto.SelfAssessmentDetailVO;
import com.insurance.aml.module.assessment.model.entity.AssessmentIndicator;
import com.insurance.aml.module.assessment.model.entity.AssessmentScore;
import com.insurance.aml.module.assessment.model.entity.SelfAssessment;
import com.insurance.aml.module.assessment.service.impl.SelfAssessmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("风险自评估服务测试")
class SelfAssessmentServiceImplTest {

    @Mock private SelfAssessmentMapper assessmentMapper;
    @Mock private AssessmentIndicatorMapper indicatorMapper;
    @Mock private AssessmentScoreMapper scoreMapper;

    @InjectMocks private SelfAssessmentServiceImpl service;

    private SelfAssessment assessment(String status) {
        SelfAssessment a = new SelfAssessment();
        a.setId(1L);
        a.setAssessmentStatus(status);
        return a;
    }

    private AssessmentScoreRequest scoreReq() {
        AssessmentScoreRequest r = new AssessmentScoreRequest();
        r.setAssessmentId(1L);
        r.setIndicatorId(10L);
        r.setScore(80);
        return r;
    }

    // ---------- submitScore ----------

    @Test
    @DisplayName("submitScore：评估不存在抛异常")
    void submitScore_assessmentNotFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("评估不存在"));
    }

    @Test
    @DisplayName("submitScore：状态为COMPLETED时拒绝评分")
    void submitScore_completedStatus_rejected() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("不允许评分"));
    }

    @Test
    @DisplayName("submitScore：CREATED自动转IN_PROGRESS")
    void submitScore_createdStatus_autoTransitions() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.CREATED.getCode()));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(null);

        service.submitScore(scoreReq());

        ArgumentCaptor<SelfAssessment> captor = ArgumentCaptor.forClass(SelfAssessment.class);
        verify(assessmentMapper).updateById(captor.capture());
        assertEquals("IN_PROGRESS", captor.getValue().getAssessmentStatus());
    }

    @Test
    @DisplayName("submitScore：指标不存在抛异常")
    void submitScore_indicatorNotFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("评估指标不存在"));
    }

    @Test
    @DisplayName("submitScore：已有评分走更新分支")
    void submitScore_existingScore_updates() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(new AssessmentScore());

        service.submitScore(scoreReq());

        verify(scoreMapper).updateById(any(AssessmentScore.class));
        verify(scoreMapper, never()).insert(any(AssessmentScore.class));
    }

    @Test
    @DisplayName("submitScore：无既有评分走新增分支")
    void submitScore_noExistingScore_inserts() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(null);

        service.submitScore(scoreReq());

        verify(scoreMapper).insert(any(AssessmentScore.class));
        verify(scoreMapper, never()).updateById(any(AssessmentScore.class));
    }

    // ---------- completeAssessment ----------

    private void stubCompleteWith(int inherentVal, int controlVal) {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        AssessmentScore s1 = new AssessmentScore();
        s1.setIndicatorId(10L); s1.setScore(inherentVal);
        AssessmentScore s2 = new AssessmentScore();
        s2.setIndicatorId(20L); s2.setScore(controlVal);
        when(scoreMapper.selectList(any())).thenReturn(List.of(s1, s2));
        AssessmentIndicator i1 = new AssessmentIndicator();
        i1.setId(10L); i1.setCategory("INHERENT_RISK"); i1.setWeight(BigDecimal.ONE);
        AssessmentIndicator i2 = new AssessmentIndicator();
        i2.setId(20L); i2.setCategory("CONTROL_EFFECTIVENESS"); i2.setWeight(BigDecimal.ONE);
        when(indicatorMapper.selectList(any())).thenReturn(List.of(i1, i2));
    }

    @Test
    @DisplayName("completeAssessment：非IN_PROGRESS拒绝")
    void completeAssessment_notInProgress_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.CREATED.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.completeAssessment(1L));
        assertTrue(ex.getMessage().contains("只有进行中"));
    }

    @Test
    @DisplayName("completeAssessment：无评分拒绝")
    void completeAssessment_noScores_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(scoreMapper.selectList(any())).thenReturn(List.of());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.completeAssessment(1L));
        assertTrue(ex.getMessage().contains("无任何评分"));
    }

    @Test
    @DisplayName("completeAssessment：综合分>70判定HIGH")
    void completeAssessment_highRisk() {
        stubCompleteWith(80, 80);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(80, result.getInherentRiskScore());
        assertEquals(80, result.getControlEffectivenessScore());
        assertEquals(80, result.getOverallScore());
        assertEquals(RiskLevel.HIGH.getCode(), result.getOverallRiskLevel());
        assertEquals(AssessmentStatusEnum.COMPLETED.getCode(), result.getAssessmentStatus());
    }

    @Test
    @DisplayName("completeAssessment：综合分40-70判定MEDIUM")
    void completeAssessment_mediumRisk() {
        stubCompleteWith(50, 50);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(50, result.getOverallScore());
        assertEquals(RiskLevel.MEDIUM.getCode(), result.getOverallRiskLevel());
    }

    @Test
    @DisplayName("completeAssessment：综合分<40判定LOW")
    void completeAssessment_lowRisk() {
        stubCompleteWith(30, 30);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(30, result.getOverallScore());
        assertEquals(RiskLevel.LOW.getCode(), result.getOverallRiskLevel());
    }

    // ---------- approveAssessment ----------

    @Test
    @DisplayName("approveAssessment：非COMPLETED拒绝")
    void approveAssessment_notCompleted_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.approveAssessment(1L, "mgr"));
        assertTrue(ex.getMessage().contains("只有已完成"));
    }

    @Test
    @DisplayName("approveAssessment：成功置APPROVED并记录审批人")
    void approveAssessment_success() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        service.approveAssessment(1L, "mgr");
        ArgumentCaptor<SelfAssessment> captor = ArgumentCaptor.forClass(SelfAssessment.class);
        verify(assessmentMapper).updateById(captor.capture());
        assertEquals(AssessmentStatusEnum.APPROVED.getCode(), captor.getValue().getAssessmentStatus());
        assertEquals("mgr", captor.getValue().getApprovedBy());
        assertNotNull(captor.getValue().getApprovedTime());
    }

    // ---------- getAssessmentDetail ----------

    @Test
    @DisplayName("getAssessmentDetail：不存在抛异常")
    void getAssessmentDetail_notFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getAssessmentDetail(1L));
        assertTrue(ex.getMessage().contains("评估不存在"));
    }

    @Test
    @DisplayName("getAssessmentDetail：装配指标明细")
    void getAssessmentDetail_assemblesIndicatorDetail() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        AssessmentScore s = new AssessmentScore();
        s.setId(100L); s.setAssessmentId(1L); s.setIndicatorId(10L); s.setScore(88);
        when(scoreMapper.selectList(any())).thenReturn(List.of(s));
        AssessmentIndicator ind = new AssessmentIndicator();
        ind.setId(10L); ind.setIndicatorCode("I1"); ind.setIndicatorName("指标1");
        ind.setCategory("INHERENT_RISK"); ind.setDimension("D1"); ind.setWeight(new BigDecimal("2"));
        when(indicatorMapper.selectList(any())).thenReturn(List.of(ind));

        SelfAssessmentDetailVO vo = service.getAssessmentDetail(1L);

        assertNotNull(vo.getScores());
        assertEquals(1, vo.getScores().size());
        assertEquals("I1", vo.getScores().get(0).getIndicatorCode());
        assertEquals("指标1", vo.getScores().get(0).getIndicatorName());
        assertEquals("INHERENT_RISK", vo.getScores().get(0).getCategory());
        assertEquals(new BigDecimal("2"), vo.getScores().get(0).getWeight());
    }
}
```

- [ ] **Step 2: 运行测试类，期望全绿**

Run: `mvn -q -Dtest=SelfAssessmentServiceImplTest test`
Expected: BUILD SUCCESS，Tests run: 15, Failures: 0, Errors: 0。
若有断言失败：核对是否为生产代码 bug → 记录为发现，**不改生产代码**，停下汇报。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/insurance/aml/module/assessment/service/SelfAssessmentServiceImplTest.java
git commit -m "test: key-path unit tests for SelfAssessmentServiceImpl

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: RectificationServiceImpl 单测

**Files:**
- Create: `src/test/java/com/insurance/aml/module/assessment/service/RectificationServiceImplTest.java`

- [ ] **Step 1: 写测试文件（全部 16 用例）**

```java
package com.insurance.aml.module.assessment.service;

import com.insurance.aml.common.enums.RectificationStatus;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.assessment.mapper.RectificationTaskMapper;
import com.insurance.aml.module.assessment.model.dto.RectificationProgressRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationVerifyRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.module.assessment.service.impl.RectificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("整改任务服务测试")
class RectificationServiceImplTest {

    @Mock private RectificationTaskMapper taskMapper;
    @InjectMocks private RectificationServiceImpl service;

    private RectificationTaskRequest taskReq() {
        RectificationTaskRequest r = new RectificationTaskRequest();
        r.setAssessmentId(1L);
        r.setIssueDescription("问题");
        r.setSeverity("HIGH");
        r.setDeadline(LocalDate.now().plusDays(30));
        return r;
    }

    private RectificationTask task(String status) {
        RectificationTask t = new RectificationTask();
        t.setId(1L);
        t.setStatus(status);
        return t;
    }

    // ---------- createTask ----------

    @Test
    @DisplayName("createTask：默认值正确")
    void createTask_defaults() {
        service.createTask(taskReq());
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).insert(captor.capture());
        RectificationTask t = captor.getValue();
        assertEquals(RectificationStatus.OPEN.getCode(), t.getStatus());
        assertEquals(0, t.getProgressPercent());
        assertEquals("PENDING", t.getVerificationStatus());
        assertEquals("SELF_ASSESSMENT", t.getSourceType());
    }

    @Test
    @DisplayName("createTask：遗留责任人姓名归一")
    void createTask_normalizesLegacyResponsiblePerson() {
        RectificationTaskRequest req = taskReq();
        req.setResponsiblePerson("admin");
        service.createTask(req);
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("刘思远", captor.getValue().getResponsiblePerson());
    }

    @Test
    @DisplayName("createTask：保留显式sourceType")
    void createTask_preservesExplicitSourceType() {
        RectificationTaskRequest req = taskReq();
        req.setSourceType("REGULATOR");
        service.createTask(req);
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("REGULATOR", captor.getValue().getSourceType());
    }

    // ---------- updateTaskStatus ----------

    @Test
    @DisplayName("updateTaskStatus：任务不存在抛异常")
    void updateTaskStatus_notFound_throws() {
        when(taskMapper.selectById(1L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateTaskStatus(1L, RectificationStatus.COMPLETED.getCode()));
        assertTrue(ex.getMessage().contains("整改任务不存在"));
    }

    @Test
    @DisplayName("updateTaskStatus：完成置完成时间与100%")
    void updateTaskStatus_completed_setsFields() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.IN_PROGRESS.getCode()));
        service.updateTaskStatus(1L, RectificationStatus.COMPLETED.getCode());
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.COMPLETED.getCode(), captor.getValue().getStatus());
        assertEquals(100, captor.getValue().getProgressPercent());
        assertNotNull(captor.getValue().getCompletedTime());
    }

    @Test
    @DisplayName("updateTaskStatus：验证置销号时间与PASSED")
    void updateTaskStatus_verified_setsFields() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.COMPLETED.getCode()));
        service.updateTaskStatus(1L, RectificationStatus.VERIFIED.getCode());
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.VERIFIED.getCode(), captor.getValue().getStatus());
        assertEquals("PASSED", captor.getValue().getVerificationStatus());
        assertNotNull(captor.getValue().getClosedTime());
    }

    // ---------- updateProgress ----------

    @Test
    @DisplayName("updateProgress：任务不存在抛异常")
    void updateProgress_notFound_throws() {
        when(taskMapper.selectById(1L)).thenReturn(null);
        RectificationProgressRequest req = new RectificationProgressRequest();
        req.setProgressPercent(50);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateProgress(1L, req));
        assertTrue(ex.getMessage().contains("整改任务不存在"));
    }

    @Test
    @DisplayName("updateProgress：进度100置COMPLETED")
    void updateProgress_complete() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.IN_PROGRESS.getCode()));
        RectificationProgressRequest req = new RectificationProgressRequest();
        req.setProgressPercent(100);
        service.updateProgress(1L, req);
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.COMPLETED.getCode(), captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCompletedTime());
    }

    @Test
    @DisplayName("updateProgress：部分进度置IN_PROGRESS")
    void updateProgress_partial() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.OPEN.getCode()));
        RectificationProgressRequest req = new RectificationProgressRequest();
        req.setProgressPercent(40);
        service.updateProgress(1L, req);
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.IN_PROGRESS.getCode(), captor.getValue().getStatus());
    }

    // ---------- verifyTask(Long, String) ----------

    @Test
    @DisplayName("verifyTask(name)：非COMPLETED拒绝")
    void verifyTaskByName_notCompleted_throws() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.IN_PROGRESS.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.verifyTask(1L, "admin"));
        assertTrue(ex.getMessage().contains("只有已完成"));
    }

    @Test
    @DisplayName("verifyTask(name)：成功置VERIFIED并归一验证人")
    void verifyTaskByName_success() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.COMPLETED.getCode()));
        service.verifyTask(1L, "admin");
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.VERIFIED.getCode(), captor.getValue().getStatus());
        assertEquals("PASSED", captor.getValue().getVerificationStatus());
        assertEquals("刘思远", captor.getValue().getVerifiedBy());
        assertNotNull(captor.getValue().getClosedTime());
    }

    // ---------- verifyTask(Long, RectificationVerifyRequest) ----------

    @Test
    @DisplayName("verifyTask(req)：非COMPLETED/VERIFIED拒绝")
    void verifyTaskByReq_invalidStatus_throws() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.OPEN.getCode()));
        RectificationVerifyRequest req = new RectificationVerifyRequest();
        req.setVerificationStatus("PASSED");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.verifyTask(1L, req));
        assertTrue(ex.getMessage().contains("只有已完成"));
    }

    @Test
    @DisplayName("verifyTask(req)：PASSED置VERIFIED并销号")
    void verifyTaskByReq_passed() {
        when(taskMapper.selectById(1L)).thenReturn(task(RectificationStatus.COMPLETED.getCode()));
        RectificationVerifyRequest req = new RectificationVerifyRequest();
        req.setVerificationStatus("PASSED");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("admin");
            service.verifyTask(1L, req);
        }
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.VERIFIED.getCode(), captor.getValue().getStatus());
        assertEquals("PASSED", captor.getValue().getVerificationStatus());
        assertNotNull(captor.getValue().getClosedTime());
    }

    @Test
    @DisplayName("verifyTask(req)：RETURNED退回IN_PROGRESS并清销号")
    void verifyTaskByReq_returned() {
        RectificationTask t = task(RectificationStatus.COMPLETED.getCode());
        t.setClosedTime(LocalDateTime.now());
        when(taskMapper.selectById(1L)).thenReturn(t);
        RectificationVerifyRequest req = new RectificationVerifyRequest();
        req.setVerificationStatus("RETURNED");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("system");
            service.verifyTask(1L, req);
        }
        ArgumentCaptor<RectificationTask> captor = ArgumentCaptor.forClass(RectificationTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(RectificationStatus.IN_PROGRESS.getCode(), captor.getValue().getStatus());
        assertNull(captor.getValue().getClosedTime());
    }

    // ---------- listTasks (逾期检测) ----------

    @Test
    @DisplayName("listTasks：过期OPEN任务标记OVERDUE")
    void listTasks_marksOverdue() {
        RectificationTask t = task(RectificationStatus.OPEN.getCode());
        t.setDeadline(LocalDate.now().minusDays(1));
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(t)));
        List<RectificationTask> result = service.listTasks(1L);
        assertEquals(RectificationStatus.OVERDUE.getCode(), result.get(0).getStatus());
        verify(taskMapper).updateById(any(RectificationTask.class));
    }

    @Test
    @DisplayName("listTasks：未过期任务不改状态")
    void listTasks_futureDeadline_unchanged() {
        RectificationTask t = task(RectificationStatus.OPEN.getCode());
        t.setDeadline(LocalDate.now().plusDays(5));
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(t)));
        List<RectificationTask> result = service.listTasks(1L);
        assertEquals(RectificationStatus.OPEN.getCode(), result.get(0).getStatus());
        verify(taskMapper, never()).updateById(any(RectificationTask.class));
    }
}
```

- [ ] **Step 2: 运行测试类，期望全绿**

Run: `mvn -q -Dtest=RectificationServiceImplTest test`
Expected: BUILD SUCCESS，Tests run: 16, Failures: 0, Errors: 0。
若断言失败：记录为发现，不改生产代码，停下汇报。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/insurance/aml/module/assessment/service/RectificationServiceImplTest.java
git commit -m "test: key-path unit tests for RectificationServiceImpl

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: SpecialPreventionServiceImpl 单测

**Files:**
- Create: `src/test/java/com/insurance/aml/module/prevention/service/SpecialPreventionServiceImplTest.java`

- [ ] **Step 1: 写测试文件（全部 15 用例）**

```java
package com.insurance.aml.module.prevention.service;

import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.casemgmt.model.dto.CaseCreateRequest;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.prevention.mapper.FreezeSeizureDeductionMapper;
import com.insurance.aml.module.prevention.mapper.RetrospectiveScreeningJobMapper;
import com.insurance.aml.module.prevention.mapper.SpecialMeasureMapper;
import com.insurance.aml.module.prevention.mapper.WatchlistUpdateJobMapper;
import com.insurance.aml.module.prevention.model.dto.FreezeSeizureDeductionRequest;
import com.insurance.aml.module.prevention.model.dto.RetrospectiveScreeningJobRequest;
import com.insurance.aml.module.prevention.model.dto.SpecialMeasureRequest;
import com.insurance.aml.module.prevention.model.dto.WatchlistSyncRequest;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
import com.insurance.aml.module.prevention.model.entity.WatchlistUpdateJob;
import com.insurance.aml.module.prevention.service.impl.SpecialPreventionServiceImpl;
import com.insurance.aml.module.screening.mapper.ScreeningResultMapper;
import com.insurance.aml.module.screening.mapper.WatchlistMapper;
import com.insurance.aml.module.screening.mapper.WatchlistSourceMapper;
import com.insurance.aml.module.screening.model.entity.ScreeningResult;
import com.insurance.aml.module.screening.model.entity.WatchlistSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("特别预防措施服务测试")
class SpecialPreventionServiceImplTest {

    @Mock private WatchlistUpdateJobMapper updateJobMapper;
    @Mock private RetrospectiveScreeningJobMapper retrospectiveJobMapper;
    @Mock private SpecialMeasureMapper specialMeasureMapper;
    @Mock private FreezeSeizureDeductionMapper freezeMapper;
    @Mock private WatchlistSourceMapper watchlistSourceMapper;
    @Mock private WatchlistMapper watchlistMapper;
    @Mock private ScreeningResultMapper screeningResultMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private AlertService alertService;
    @Mock private AlertMapper alertMapper;
    @Mock private CaseService caseService;
    @Mock private IdGenerator idGenerator;

    @InjectMocks private SpecialPreventionServiceImpl service;

    private Customer customer() {
        Customer c = new Customer();
        c.setId(2L);
        c.setName("张三");
        return c;
    }

    private ScreeningResult screeningResult(BigDecimal matchScore) {
        ScreeningResult r = new ScreeningResult();
        r.setId(1L);
        r.setCustomerId(2L);
        r.setCustomerName("张三");
        r.setWatchlistName("制裁名单");
        r.setMatchScore(matchScore);
        return r;
    }

    // ---------- createWatchlistUpdateJob ----------

    @Test
    @DisplayName("名单更新：名单源不存在抛BusinessException")
    void createWatchlistUpdateJob_sourceNotFound_throws() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setSourceId(99L);
        req.setUpdateMode("MANUAL");
        when(watchlistSourceMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createWatchlistUpdateJob(req));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("名单更新：sourceId为空使用全部名单源且无源副作用")
    void createWatchlistUpdateJob_nullSource_usesAllSources() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setUpdateMode("MANUAL");
        when(watchlistMapper.selectCount(any())).thenReturn(5L);
        when(idGenerator.generate("WUJ")).thenReturn("WUJ1");
        service.createWatchlistUpdateJob(req);
        ArgumentCaptor<WatchlistUpdateJob> captor = ArgumentCaptor.forClass(WatchlistUpdateJob.class);
        verify(updateJobMapper).insert(captor.capture());
        assertEquals("全部名单源", captor.getValue().getSourceName());
        assertEquals(5, captor.getValue().getTotalEntries());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        verify(watchlistSourceMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("名单更新：有效名单源更新源统计")
    void createWatchlistUpdateJob_withSource_updatesSource() {
        WatchlistSyncRequest req = new WatchlistSyncRequest();
        req.setSourceId(1L);
        req.setUpdateMode("MANUAL");
        WatchlistSource source = new WatchlistSource();
        source.setSourceName("制裁名单");
        when(watchlistSourceMapper.selectById(1L)).thenReturn(source);
        when(watchlistMapper.selectCount(any())).thenReturn(3L);
        when(idGenerator.generate("WUJ")).thenReturn("WUJ2");
        service.createWatchlistUpdateJob(req);
        ArgumentCaptor<WatchlistUpdateJob> jobCaptor = ArgumentCaptor.forClass(WatchlistUpdateJob.class);
        verify(updateJobMapper).insert(jobCaptor.capture());
        assertEquals("制裁名单", jobCaptor.getValue().getSourceName());
        assertEquals(3, jobCaptor.getValue().getTotalEntries());
        ArgumentCaptor<WatchlistSource> srcCaptor = ArgumentCaptor.forClass(WatchlistSource.class);
        verify(watchlistSourceMapper).updateById(srcCaptor.capture());
        assertEquals(3, srcCaptor.getValue().getTotalEntries());
        assertNotNull(srcCaptor.getValue().getLastUpdateTime());
    }

    // ---------- createRetrospectiveJob / countCustomersByScope ----------

    @Test
    @DisplayName("回溯筛查：CUSTOMER_IDS按逗号计数")
    void createRetrospectiveJob_customerIds_countsByComma() {
        RetrospectiveScreeningJobRequest req = new RetrospectiveScreeningJobRequest();
        req.setJobName("J1");
        req.setScopeType("CUSTOMER_IDS");
        req.setCustomerIds("1,2,3");
        when(screeningResultMapper.selectCount(any())).thenReturn(0L);
        when(idGenerator.generate("RSJ")).thenReturn("RSJ1");
        service.createRetrospectiveJob(req);
        ArgumentCaptor<RetrospectiveScreeningJob> captor = ArgumentCaptor.forClass(RetrospectiveScreeningJob.class);
        verify(retrospectiveJobMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getTotalCustomers());
        assertEquals(3, captor.getValue().getProcessedCustomers());
    }

    @Test
    @DisplayName("回溯筛查：HIGH_RISK经客户Mapper计数")
    void createRetrospectiveJob_highRisk_countsViaMapper() {
        RetrospectiveScreeningJobRequest req = new RetrospectiveScreeningJobRequest();
        req.setJobName("J2");
        req.setScopeType("HIGH_RISK");
        when(customerMapper.selectCount(any())).thenReturn(7L);
        when(screeningResultMapper.selectCount(any())).thenReturn(2L);
        when(idGenerator.generate("RSJ")).thenReturn("RSJ2");
        service.createRetrospectiveJob(req);
        ArgumentCaptor<RetrospectiveScreeningJob> captor = ArgumentCaptor.forClass(RetrospectiveScreeningJob.class);
        verify(retrospectiveJobMapper).insert(captor.capture());
        assertEquals(7, captor.getValue().getTotalCustomers());
        assertEquals(2, captor.getValue().getTotalHits());
    }

    // ---------- createSpecialMeasure / updateSpecialMeasureStatus ----------

    @Test
    @DisplayName("特别措施：客户不存在抛BusinessException")
    void createSpecialMeasure_customerNotFound_throws() {
        SpecialMeasureRequest req = new SpecialMeasureRequest();
        req.setCustomerId(99L);
        when(customerMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSpecialMeasure(req));
        assertEquals(ResultCode.CUSTOMER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("特别措施：controlLevel缺省为MEDIUM")
    void createSpecialMeasure_defaultControlLevel() {
        SpecialMeasureRequest req = new SpecialMeasureRequest();
        req.setCustomerId(2L);
        when(customerMapper.selectById(2L)).thenReturn(customer());
        when(idGenerator.generate("SPM")).thenReturn("SPM1");
        service.createSpecialMeasure(req);
        ArgumentCaptor<SpecialMeasure> captor = ArgumentCaptor.forClass(SpecialMeasure.class);
        verify(specialMeasureMapper).insert(captor.capture());
        assertEquals("MEDIUM", captor.getValue().getControlLevel());
    }

    @Test
    @DisplayName("特别措施：状态更新目标不存在抛异常")
    void updateSpecialMeasureStatus_notFound_throws() {
        when(specialMeasureMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSpecialMeasureStatus(1L, "CLOSED", "reason"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- createFreezeRecord / updateFreezeRecordStatus ----------

    @Test
    @DisplayName("查冻扣：客户不存在抛BusinessException")
    void createFreezeRecord_customerNotFound_throws() {
        FreezeSeizureDeductionRequest req = new FreezeSeizureDeductionRequest();
        req.setCustomerId(99L);
        when(customerMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createFreezeRecord(req));
        assertEquals(ResultCode.CUSTOMER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("查冻扣：currency缺省为CNY")
    void createFreezeRecord_defaultCurrency() {
        FreezeSeizureDeductionRequest req = new FreezeSeizureDeductionRequest();
        req.setCustomerId(2L);
        when(customerMapper.selectById(2L)).thenReturn(customer());
        when(idGenerator.generate("FSD")).thenReturn("FSD1");
        service.createFreezeRecord(req);
        ArgumentCaptor<FreezeSeizureDeduction> captor = ArgumentCaptor.forClass(FreezeSeizureDeduction.class);
        verify(freezeMapper).insert(captor.capture());
        assertEquals("CNY", captor.getValue().getCurrency());
    }

    @Test
    @DisplayName("查冻扣：状态更新目标不存在抛异常")
    void updateFreezeRecordStatus_notFound_throws() {
        when(freezeMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateFreezeRecordStatus(1L, "RELEASED", "remark"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- escalateScreeningResultToAlert ----------

    @Test
    @DisplayName("升级预警：筛查结果不存在抛BusinessException")
    void escalate_resultNotFound_throws() {
        when(screeningResultMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.escalateScreeningResultToAlert(1L, "reason"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("升级预警：成功置ESCALATED并创建预警（分<95风险HIGH）")
    void escalate_success_setsEscalatedHighRisk() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("90")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        Alert result;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            result = service.escalateScreeningResultToAlert(1L, "reason");
        }
        assertSame(created, result);
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.HIGH.getCode(), alertCaptor.getValue().getRiskLevel());
        assertEquals("SCREENING:1", alertCaptor.getValue().getDeduplicateKey());
        ArgumentCaptor<ScreeningResult> resCaptor = ArgumentCaptor.forClass(ScreeningResult.class);
        verify(screeningResultMapper).updateById(resCaptor.capture());
        assertEquals("ESCALATED", resCaptor.getValue().getReviewStatus());
        assertEquals("compliance", resCaptor.getValue().getReviewedBy());
    }

    // ---------- createCaseFromScreeningResult ----------

    @Test
    @DisplayName("筛查建案：高分(≥95)风险CRITICAL优先级5")
    void createCase_highScore_priority5() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("96")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        when(caseService.createCase(any(CaseCreateRequest.class))).thenReturn(new Case());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.createCaseFromScreeningResult(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.CRITICAL.getCode(), alertCaptor.getValue().getRiskLevel());
        ArgumentCaptor<CaseCreateRequest> caseCaptor = ArgumentCaptor.forClass(CaseCreateRequest.class);
        verify(caseService).createCase(caseCaptor.capture());
        assertEquals(5, caseCaptor.getValue().getPriority());
        assertEquals("WATCHLIST_HIT", caseCaptor.getValue().getCaseType());
    }

    @Test
    @DisplayName("筛查建案：低分(<95)风险HIGH优先级4")
    void createCase_lowScore_priority4() {
        when(screeningResultMapper.selectById(1L)).thenReturn(screeningResult(new BigDecimal("80")));
        Alert created = new Alert();
        created.setId(500L);
        when(alertService.createAlert(any(Alert.class), anyList())).thenReturn(created);
        when(caseService.createCase(any(CaseCreateRequest.class))).thenReturn(new Case());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUsername).thenReturn("compliance");
            service.createCaseFromScreeningResult(1L, "reason");
        }
        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertService).createAlert(alertCaptor.capture(), anyList());
        assertEquals(RiskLevel.HIGH.getCode(), alertCaptor.getValue().getRiskLevel());
        ArgumentCaptor<CaseCreateRequest> caseCaptor = ArgumentCaptor.forClass(CaseCreateRequest.class);
        verify(caseService).createCase(caseCaptor.capture());
        assertEquals(4, caseCaptor.getValue().getPriority());
    }
}
```

- [ ] **Step 2: 运行测试类，期望全绿**

Run: `mvn -q -Dtest=SpecialPreventionServiceImplTest test`
Expected: BUILD SUCCESS，Tests run: 15, Failures: 0, Errors: 0。
若断言失败：记录为发现，不改生产代码，停下汇报。

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/insurance/aml/module/prevention/service/SpecialPreventionServiceImplTest.java
git commit -m "test: key-path unit tests for SpecialPreventionServiceImpl

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 全量回归与收尾

**Files:** 无（仅验证）

- [ ] **Step 1: 全量测试**

Run: `mvn -q test`
Expected: BUILD SUCCESS，Tests run 较基线 275 增加 46（约 321），Failures: 0, Errors: 0。

- [ ] **Step 2: 确认生产代码零改动**

Run: `git diff --name-only main...HEAD`
Expected: 仅 `docs/superpowers/...` 与 `src/test/java/...` 路径；无 `src/main/java/...`。

- [ ] **Step 3: 若有偏差发现，追加记录**

若 Task 1-3 任一暴露实现/应然行为偏差：在 `docs/development/全量分析报告-20260616.md` 的 backlog 段追加一条发现（标题 + 文件 + 现象 + 建议），单独 commit；不在本轮修生产代码。
若无偏差：跳过此步。

---

## Self-Review（计划编写者已执行）

**1. Spec coverage：** spec §3.1/3.2/3.3 三张表每行均映射到 Task 1/2/3 的具体 `@Test`（SelfAssessment 15、Rectification 16、SpecialPrevention 15，合 46）。spec §6 验收 1-5 由 Task 4 覆盖（全量绿、生产零改动、偏差记录）。

**2. Placeholder scan：** 无 TBD/TODO；所有测试代码完整可编译；所有 run 命令含期望输出。

**3. Type consistency：** 已逐一核对源码——枚举码（AssessmentStatusEnum/RectificationStatus/RiskLevel）、异常类型（RuntimeException vs BusinessException + ResultCode 码）、字段名（getSourceName/getTotalEntries/getTotalCustomers/getProcessedCustomers/getTotalHits/getControlLevel/getCurrency/getPriority/getCaseType/getDeduplicateKey/getRiskLevel/getScores/getIndicatorCode/getIndicatorName/getCategory/getWeight）、方法签名（`createAlert(Alert, List)`、`createCase(CaseCreateRequest)`、`generate(String)`、`getCurrentUsername()`）、构造注入（SpecialPrevention 12 个依赖全部 @Mock）。`Customer`/`RectificationTask` 经 `BaseEntity` 提供 `setId`。
