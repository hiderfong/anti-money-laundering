package com.insurance.aml.module.monitoring.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Drools规则评估结果
 * 作为Working Memory中的事实对象，由Drools规则填充匹配结果
 */
@Data
public class DroolsRuleResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易ID
     */
    private Long transactionId;

    /**
     * 交易流水号
     */
    private String transactionNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 命中的规则编码列表
     */
    private List<String> matchedRuleCodes = new ArrayList<>();

    /**
     * 命中的规则名称列表
     */
    private List<String> matchedRuleNames = new ArrayList<>();

    /**
     * 综合风险评分（0-100）
     */
    private BigDecimal totalRiskScore = BigDecimal.ZERO;

    /**
     * 评估详情列表
     */
    private List<String> evaluationDetails = new ArrayList<>();

    /**
     * 是否有规则命中
     */
    public boolean hasMatch() {
        return matchedRuleCodes != null && !matchedRuleCodes.isEmpty();
    }

    /**
     * 添加规则命中结果
     *
     * @param ruleCode 规则编码
     * @param ruleName 规则名称
     * @param riskScore 风险评分
     * @param detail 命中详情
     */
    public void addMatch(String ruleCode, String ruleName, BigDecimal riskScore, String detail) {
        this.matchedRuleCodes.add(ruleCode);
        this.matchedRuleNames.add(ruleName);
        this.totalRiskScore = this.totalRiskScore.add(riskScore);
        this.evaluationDetails.add(String.format("[%s] %s", ruleCode, detail));
    }
}
