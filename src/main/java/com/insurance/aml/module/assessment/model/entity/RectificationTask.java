package com.insurance.aml.module.assessment.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 整改任务实体
 * 记录风险自评估中发现问题的整改跟踪
 */
@Data
@TableName("t_rectification_task")
public class RectificationTask extends BaseEntity {

    /**
     * 关联评估ID
     */
    private Long assessmentId;

    /**
     * 问题描述
     */
    private String issueDescription;

    /**
     * 严重程度：HIGH-高、MEDIUM-中、LOW-低
     */
    private String severity;

    /**
     * 责任部门
     */
    private String responsibleDept;

    /**
     * 责任人
     */
    private String responsiblePerson;

    /**
     * 整改期限
     */
    private LocalDate deadline;

    /**
     * 状态：OPEN-待处理、IN_PROGRESS-进行中、COMPLETED-已完成、OVERDUE-已逾期
     */
    private String status;

    /**
     * 完成证据
     */
    private String completionEvidence;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;

    /**
     * 验证人
     */
    private String verifiedBy;

    /**
     * 验证时间
     */
    private LocalDateTime verifiedTime;
}
