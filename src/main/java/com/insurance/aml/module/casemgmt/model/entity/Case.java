package com.insurance.aml.module.casemgmt.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案件实体
 * 记录反洗钱调查案件的完整信息
 */
@Data
@TableName("t_case")
public class Case {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 案件编号（业务编号，如CAS20260101120000123456）
     */
    private String caseNo;

    /**
     * 关联告警ID
     */
    private Long alertId;

    /**
     * 关联客户ID
     */
    private Long customerId;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 案件状态：DRAFT-草稿、INVESTIGATING-调查中、PENDING_APPROVAL-待审批、SUBMITTED-已提交、CLOSED-已关闭
     */
    private String caseStatus;

    /**
     * 案件类型
     */
    private String caseType;

    /**
     * 优先级（1-5，5最高）
     */
    private Integer priority;

    /**
     * 案件摘要
     */
    private String summary;

    /**
     * 调查员ID
     */
    private Long investigatorId;

    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 关闭时间
     */
    private LocalDateTime closeTime;

    /**
     * 关闭原因
     */
    private String closeReason;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
