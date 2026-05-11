package com.insurance.aml.module.monitoring.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 规则执行日志实体
 * 记录每次规则匹配的详细信息
 */
@Data
@TableName("t_rule_execution_log")
public class RuleExecutionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则编码
     */
    private String ruleCode;

    /**
     * 交易ID
     */
    private Long transactionId;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 执行时间
     */
    private LocalDateTime executionTime;

    /**
     * 是否命中规则
     */
    private Boolean matchResult;

    /**
     * 匹配得分
     */
    private BigDecimal matchScore;

    /**
     * 执行详情
     */
    private String executionDetail;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
