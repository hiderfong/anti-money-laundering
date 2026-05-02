package com.insurance.aml.module.assessment.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 风险自评估实体
 * 记录年度/季度风险自评估的总体信息
 */
@Data
@TableName("t_self_assessment")
public class SelfAssessment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 评估年度
     */
    private Integer assessmentYear;

    /**
     * 评估周期：ANNUAL-年度、QUARTERLY-季度
     */
    private String assessmentPeriod;

    /**
     * 评估状态：DRAFT-草稿、IN_PROGRESS-进行中、COMPLETED-已完成、APPROVED-已审批
     */
    private String assessmentStatus;

    /**
     * 评估人ID
     */
    private Long assessorId;

    /**
     * 固有风险评分
     */
    private Integer inherentRiskScore;

    /**
     * 控制有效性评分
     */
    private Integer controlEffectivenessScore;

    /**
     * 综合评分
     */
    private Integer overallScore;

    /**
     * 综合风险等级
     */
    private String overallRiskLevel;

    /**
     * 评估结论
     */
    private String conclusion;

    /**
     * 审批人
     */
    private String approvedBy;

    /**
     * 审批时间
     */
    private LocalDateTime approvedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
