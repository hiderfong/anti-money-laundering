package com.insurance.aml.module.casemgmt.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可疑交易报告（STR）实体
 * 记录可疑交易报告的完整信息，包括撰写、审核、提交流程
 */
@Data
@TableName("t_str_report")
public class StrReport {

    /**
     * 主键ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 报告编号（业务编号，如RPT20260101120000123456）
     */
    private String reportNo;

    /**
     * 关联案件ID
     */
    private Long caseId;

    /**
     * 关联客户ID
     */
    private Long customerId;

    /**
     * 报告类型：NORMAL-常规、URGENT-紧急
     */
    private String reportType;

    /**
     * 报告状态：DRAFT-草稿、PENDING_REVIEW-待审核、APPROVED-已批准、REJECTED-已拒绝、SUBMITTED-已提交
     */
    private String reportStatus;

    /**
     * 报告内容
     */
    private String reportContent;

    /**
     * 分析意见
     */
    private String analysisOpinion;

    /**
     * 已采取措施
     */
    private String measuresTaken;

    /**
     * 撰写人ID
     */
    private Long writerId;

    /**
     * 撰写时间
     */
    private LocalDateTime writerTime;

    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审核意见
     */
    private String reviewerOpinion;

    /**
     * 审核时间
     */
    private LocalDateTime reviewerTime;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批意见
     */
    private String approverOpinion;

    /**
     * 审批时间
     */
    private LocalDateTime approverTime;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 提交结果
     */
    private String submitResult;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
