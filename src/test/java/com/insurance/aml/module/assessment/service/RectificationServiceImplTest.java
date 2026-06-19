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
        assertEquals(100, captor.getValue().getProgressPercent());
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
        assertEquals(40, captor.getValue().getProgressPercent());
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
        assertEquals("刘思远", captor.getValue().getVerifiedBy());
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
        assertEquals("RETURNED", captor.getValue().getVerificationStatus());
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

    @Test
    @DisplayName("listTasks：截止日=今天不算逾期（isBefore为严格小于的边界）")
    void listTasks_deadlineToday_notOverdue() {
        RectificationTask t = task(RectificationStatus.OPEN.getCode());
        t.setDeadline(LocalDate.now());
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(t)));
        List<RectificationTask> result = service.listTasks(1L);
        assertEquals(RectificationStatus.OPEN.getCode(), result.get(0).getStatus());
        verify(taskMapper, never()).updateById(any(RectificationTask.class));
    }

    @Test
    @DisplayName("listTasks：已完成的过期任务不改为逾期")
    void listTasks_completedPastDeadline_notOverdue() {
        RectificationTask t = task(RectificationStatus.COMPLETED.getCode());
        t.setDeadline(LocalDate.now().minusDays(10));
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(t)));
        List<RectificationTask> result = service.listTasks(1L);
        assertEquals(RectificationStatus.COMPLETED.getCode(), result.get(0).getStatus());
        verify(taskMapper, never()).updateById(any(RectificationTask.class));
    }
}
