package com.insurance.aml.module.monitoring.service.support;

import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 规则执行日志构造辅助逻辑。
 */
public final class RuleExecutionLogFactory {

    private RuleExecutionLogFactory() {
    }

    public static RuleExecutionLog baseLog(RuleDefinition rule, Transaction transaction) {
        RuleExecutionLog execLog = new RuleExecutionLog();
        execLog.setRuleId(rule.getId());
        execLog.setRuleCode(rule.getRuleCode());
        execLog.setTransactionId(transaction.getId());
        execLog.setCustomerId(transaction.getCustomerId());
        execLog.setExecutionTime(LocalDateTime.now());
        execLog.setMatchResult(false);
        execLog.setMatchScore(BigDecimal.ZERO);
        return execLog;
    }
}
