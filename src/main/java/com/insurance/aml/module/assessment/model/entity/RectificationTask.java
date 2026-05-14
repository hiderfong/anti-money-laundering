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
     * 关联评估ID，独立整改任务可为空
     */
    private Long assessmentId;

    /**
     * 问题来源：SELF_ASSESSMENT/INTERNAL_CHECK/EXTERNAL_CHECK/REGULATOR/AUDIT
     */
    private String sourceType;

    /**
     * 来源业务ID
     */
    private Long sourceId;

    /**
     * 问题描述
     */
    private String issueDescription;

    /**
     * 问题分类
     */
    private String issueCategory;

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
     * 整改进度百分比
     */
    private Integer progressPercent;

    /**
     * 完成证据
     */
    private String completionEvidence;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;

    /**
     * 验证状态：PENDING/PASSED/RETURNED
     */
    private String verificationStatus;

    /**
     * 验证人
     */
    private String verifiedBy;

    /**
     * 验证时间
     */
    private LocalDateTime verifiedTime;

    /**
     * 验证意见
     */
    private String verifyResult;

    /**
     * 销号时间
     */
    private LocalDateTime closedTime;
}
