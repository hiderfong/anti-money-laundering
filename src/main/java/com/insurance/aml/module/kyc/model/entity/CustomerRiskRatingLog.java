package com.insurance.aml.module.kyc.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户风险评级变更日志
 */
@Data
@TableName("t_customer_risk_rating_log")
public class CustomerRiskRatingLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 变更前风险等级
     */
    private String oldRiskLevel;

    /**
     * 变更后风险等级
     */
    private String newRiskLevel;

    /**
     * 变更前风险评分
     */
    private Integer oldRiskScore;

    /**
     * 变更后风险评分
     */
    private Integer newRiskScore;

    /**
     * 变更原因
     */
    private String changeReason;

    /**
     * 变更类型：AUTO-自动，MANUAL-手动
     */
    private String changeType;

    /**
     * 变更人
     */
    private String changedBy;

    /**
     * 变更时间
     */
    private LocalDateTime changedTime;
}
