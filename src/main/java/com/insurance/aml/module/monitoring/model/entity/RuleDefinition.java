package com.insurance.aml.module.monitoring.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 规则定义实体
 * 定义反洗钱监测规则，支持大额交易、可疑交易、频率等多种规则类型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_rule_definition")
public class RuleDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 规则编码
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则分类
     * LARGE_TXN-大额交易, SUSPICIOUS-可疑交易, VELOCITY-频率检测,
     * THRESHOLD-阈值检测, CORRELATION-关联分析
     */
    private String ruleCategory;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 风险权重（1-100）
     */
    private Integer riskWeight;

    /**
     * 优先级（数值越小优先级越高）
     */
    private Integer priority;

    /**
     * 规则状态
     * ENABLED-启用, DISABLED-禁用
     */
    private String status;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expiryDate;

    /**
     * 规则配置（JSON格式）
     * 存储规则的条件参数，如阈值、时间窗口等
     */
    private String configJson;

    /**
     * Drools规则脚本（预留）
     * 后续集成Drools规则引擎时使用
     */
    private String droolsDrl;
}
