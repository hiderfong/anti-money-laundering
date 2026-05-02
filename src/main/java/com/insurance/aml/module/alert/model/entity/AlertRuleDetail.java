package com.insurance.aml.module.alert.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警规则明细实体
 */
@Data
@TableName("t_alert_rule_detail")
public class AlertRuleDetail {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 预警ID */
    private Long alertId;

    /** 规则ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 匹配评分 */
    private BigDecimal matchScore;

    /** 匹配详情 */
    private String matchDetail;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
