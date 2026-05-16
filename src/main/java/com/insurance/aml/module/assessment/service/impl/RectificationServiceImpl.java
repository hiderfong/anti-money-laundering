package com.insurance.aml.module.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.assessment.model.dto.RectificationProgressRequest;
import com.insurance.aml.module.assessment.mapper.RectificationTaskMapper;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationVerifyRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.common.enums.RectificationStatus;
import com.insurance.aml.module.assessment.service.RectificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 整改任务服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RectificationServiceImpl implements RectificationService {

    private static final Map<String, String> LEGACY_OPERATOR_NAMES = Map.of(
            "admin", "刘思远",
            "system", "系统自动处理",
            "e2e_admin", "刘思远",
            "e2e_seed_operator", "周明哲",
            "e2e_compliance", "赵清妍",
            "e2e_investigator", "陈立行",
            "e2e_viewer", "李若宁"
    );

    private final RectificationTaskMapper taskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RectificationTask createTask(RectificationTaskRequest req) {
        log.info("创建整改任务，assessmentId={}, severity={}", req.getAssessmentId(), req.getSeverity());

        RectificationTask task = new RectificationTask();
        task.setAssessmentId(req.getAssessmentId());
        task.setSourceType(StringUtils.hasText(req.getSourceType()) ? req.getSourceType() : "SELF_ASSESSMENT");
        task.setSourceId(req.getSourceId());
        task.setIssueDescription(req.getIssueDescription());
        task.setIssueCategory(req.getIssueCategory());
        task.setSeverity(req.getSeverity());
        task.setResponsibleDept(req.getResponsibleDept());
        task.setResponsiblePerson(normalizeLegacyOperatorName(req.getResponsiblePerson()));
        task.setDeadline(req.getDeadline());
        task.setStatus(RectificationStatus.OPEN.getCode());
        task.setProgressPercent(0);
        task.setVerificationStatus("PENDING");
        taskMapper.insert(task);

        log.info("整改任务创建成功，id={}", task.getId());
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskStatus(Long taskId, String status) {
        log.info("更新整改任务状态，taskId={}, status={}", taskId, status);

        RectificationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("整改任务不存在，id=" + taskId);
        }

        task.setStatus(status);
        if (RectificationStatus.COMPLETED.getCode().equals(status)) {
            task.setCompletedTime(LocalDateTime.now());
            task.setProgressPercent(100);
        }
        if (RectificationStatus.VERIFIED.getCode().equals(status)) {
            task.setClosedTime(LocalDateTime.now());
            task.setVerificationStatus("PASSED");
        }
        taskMapper.updateById(task);

        log.info("整改任务状态更新成功，taskId={}", taskId);
    }

    @Override
    public List<RectificationTask> listTasks(Long assessmentId) {
        log.info("查询整改任务列表，assessmentId={}", assessmentId);

        LambdaQueryWrapper<RectificationTask> wrapper = new LambdaQueryWrapper<>();
        if (assessmentId != null) {
            wrapper.eq(RectificationTask::getAssessmentId, assessmentId);
        }
        wrapper.orderByDesc(RectificationTask::getCreatedTime);
        List<RectificationTask> tasks = taskMapper.selectList(wrapper);

        // 逾期检测：状态为OPEN或IN_PROGRESS且已过截止日期的任务，标记为OVERDUE
        LocalDate today = LocalDate.now();
        for (RectificationTask task : tasks) {
            if ((RectificationStatus.OPEN.getCode().equals(task.getStatus()) || RectificationStatus.IN_PROGRESS.getCode().equals(task.getStatus()))
                    && task.getDeadline() != null
                    && task.getDeadline().isBefore(today)) {
                task.setStatus(RectificationStatus.OVERDUE.getCode());
                taskMapper.updateById(task);
            }
        }

        tasks.forEach(this::normalizeLegacyDisplayFields);
        return tasks;
    }

    @Override
    public PageResult<RectificationTask> pageTasks(PageQuery pageQuery, String sourceType, String status, String responsiblePerson) {
        LambdaQueryWrapper<RectificationTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(RectificationTask::getSourceType, sourceType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RectificationTask::getStatus, status);
        }
        if (StringUtils.hasText(responsiblePerson)) {
            wrapper.like(RectificationTask::getResponsiblePerson, responsiblePerson);
        }
        wrapper.orderByDesc(RectificationTask::getCreatedTime);

        IPage<RectificationTask> page = taskMapper.selectPage(pageQuery.toPage(), wrapper);
        refreshOverdueStatus(page.getRecords());
        page.getRecords().forEach(this::normalizeLegacyDisplayFields);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long taskId, RectificationProgressRequest req) {
        RectificationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("整改任务不存在，id=" + taskId);
        }

        task.setProgressPercent(req.getProgressPercent());
        if (StringUtils.hasText(req.getCompletionEvidence())) {
            task.setCompletionEvidence(req.getCompletionEvidence());
        }
        if (StringUtils.hasText(req.getStatus())) {
            task.setStatus(req.getStatus());
        } else if (req.getProgressPercent() >= 100) {
            task.setStatus(RectificationStatus.COMPLETED.getCode());
        } else if (req.getProgressPercent() > 0) {
            task.setStatus(RectificationStatus.IN_PROGRESS.getCode());
        }
        if (RectificationStatus.COMPLETED.getCode().equals(task.getStatus())) {
            task.setCompletedTime(LocalDateTime.now());
        }
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyTask(Long taskId, String verifiedBy) {
        log.info("验证整改任务，taskId={}, verifiedBy={}", taskId, verifiedBy);

        RectificationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("整改任务不存在，id=" + taskId);
        }
        if (!RectificationStatus.COMPLETED.getCode().equals(task.getStatus())) {
            throw new RuntimeException("只有已完成的任务才能验证，当前状态=" + task.getStatus());
        }

        task.setVerifiedBy(normalizeLegacyOperatorName(verifiedBy));
        task.setVerifiedTime(LocalDateTime.now());
        task.setVerificationStatus("PASSED");
        task.setStatus(RectificationStatus.VERIFIED.getCode());
        task.setClosedTime(LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("整改任务验证完成，taskId={}", taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyTask(Long taskId, RectificationVerifyRequest req) {
        RectificationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("整改任务不存在，id=" + taskId);
        }
        if (!RectificationStatus.COMPLETED.getCode().equals(task.getStatus())
                && !RectificationStatus.VERIFIED.getCode().equals(task.getStatus())) {
            throw new RuntimeException("只有已完成的任务才能验证，当前状态=" + task.getStatus());
        }

        task.setVerificationStatus(req.getVerificationStatus());
        task.setVerifyResult(req.getVerifyResult());
        task.setVerifiedBy(normalizeLegacyOperatorName(SecurityUtils.getCurrentUsername()));
        task.setVerifiedTime(LocalDateTime.now());
        if ("PASSED".equals(req.getVerificationStatus())) {
            task.setStatus(RectificationStatus.VERIFIED.getCode());
            task.setClosedTime(LocalDateTime.now());
        } else if ("RETURNED".equals(req.getVerificationStatus())) {
            task.setStatus(RectificationStatus.IN_PROGRESS.getCode());
            task.setClosedTime(null);
        }
        taskMapper.updateById(task);
    }

    private void refreshOverdueStatus(List<RectificationTask> tasks) {
        LocalDate today = LocalDate.now();
        for (RectificationTask task : tasks) {
            if ((RectificationStatus.OPEN.getCode().equals(task.getStatus()) || RectificationStatus.IN_PROGRESS.getCode().equals(task.getStatus()))
                    && task.getDeadline() != null
                    && task.getDeadline().isBefore(today)) {
                task.setStatus(RectificationStatus.OVERDUE.getCode());
                taskMapper.updateById(task);
            }
        }
    }

    private void normalizeLegacyDisplayFields(RectificationTask task) {
        task.setResponsiblePerson(normalizeLegacyOperatorName(task.getResponsiblePerson()));
        task.setVerifiedBy(normalizeLegacyOperatorName(task.getVerifiedBy()));
    }

    private String normalizeLegacyOperatorName(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return LEGACY_OPERATOR_NAMES.getOrDefault(value, value);
    }
}
