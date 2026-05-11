package com.insurance.aml.module.product.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品风险评估记录实体
 * 记录每次产品风险评估的详细评分和结果
 */
@Data
@TableName("t_product_risk_assessment")
public class ProductRiskAssessment extends BaseEntity {

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 评估日期
     */
    private LocalDate assessmentDate;

    /**
     * 评估人
     */
    private String assessor;

    /**
     * 客户群体评分（满分100）
     */
    private Integer clientGroupScore;

    /**
     * 缴费方式评分（满分100）
     */
    private Integer paymentModeScore;

    /**
     * 产品结构评分（满分100）
     */
    private Integer productStructureScore;

    /**
     * 退保风险评分（满分100）
     */
    private Integer surrenderScore;

    /**
     * 受益人风险评分（满分100）
     */
    private Integer beneficiaryScore;

    /**
     * 销售渠道评分（满分100）
     */
    private Integer channelScore;

    /**
     * 加权总评分
     */
    private Integer totalScore;

    /**
     * 风险等级：LOW-低、MEDIUM-中、HIGH-高
     */
    private String riskLevel;

    /**
     * 评估结果说明
     */
    private String assessmentResult;

    /**
     * 审批人
     */
    private String approvedBy;

    /**
     * 审批时间
     */
    private LocalDateTime approvedTime;

    /**
     * 状态：DRAFT-草稿、APPROVED-已审批、REJECTED-已驳回
     */
    private String status;
}
