package com.insurance.aml.module.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.assessment.mapper.RectificationTaskMapper;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.module.assessment.service.RectificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 整改任务服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RectificationServiceImpl implements RectificationService {

    private final RectificationTaskMapper taskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RectificationTask createTask(RectificationTaskRequest req) {
        log.info("创建整改任务，assessmentId={}, severity={}", req.getAssessmentId(), req.getSeverity());

        RectificationTask task = new RectificationTask();
        task.setAssessmentId(req.getAssessmentId());
        task.setIssueDescription(req.getIssueDescription());
        task.setSeverity(req.getSeverity());
        task.setResponsibleDept(req.getResponsibleDept());
        task.setResponsiblePerson(req.getResponsiblePerson());
        task.setDeadline(req.getDeadline());
        task.setStatus("OPEN");
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
        if ("COMPLETED".equals(status)) {
            task.setCompletedTime(LocalDateTime.now());
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
            if (("OPEN".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()))
                    && task.getDeadline() != null
                    && task.getDeadline().isBefore(today)) {
                task.setStatus("OVERDUE");
                taskMapper.updateById(task);
            }
        }

        return tasks;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyTask(Long taskId, String verifiedBy) {
        log.info("验证整改任务，taskId={}, verifiedBy={}", taskId, verifiedBy);

        RectificationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("整改任务不存在，id=" + taskId);
        }
        if (!"COMPLETED".equals(task.getStatus())) {
            throw new RuntimeException("只有已完成的任务才能验证，当前状态=" + task.getStatus());
        }

        task.setVerifiedBy(verifiedBy);
        task.setVerifiedTime(LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("整改任务验证完成，taskId={}", taskId);
    }
}
