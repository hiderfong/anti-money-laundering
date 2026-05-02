package com.insurance.aml.module.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎服务
 * 负责加载规则定义并执行规则匹配
 * 当前版本实现基础的THRESHOLD和LARGE_TXN规则，后续P2阶段集成Drools
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleExecutionLogMapper ruleExecutionLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 对交易执行所有启用的规则
     *
     * @param transaction 待评估的交易
     * @return 匹配到的规则执行日志列表
     */
    public List<RuleExecutionLog> evaluate(Transaction transaction) {
        log.info("规则引擎开始评估: transactionId={}, transactionNo={}", transaction.getId(), transaction.getTransactionNo());

        long startTime = System.currentTimeMillis();
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        // 1. 加载所有启用的规则
        List<RuleDefinition> activeRules = loadActiveRules();
        if (activeRules.isEmpty()) {
            log.debug("无启用的规则，跳过评估");
            return matchedLogs;
        }
        log.debug("加载到 {} 条启用规则", activeRules.size());

        // 2. 逐条执行规则
        for (RuleDefinition rule : activeRules) {
            try {
                RuleExecutionLog execLog = evaluateRule(rule, transaction);
                if (execLog.getMatchResult()) {
                    matchedLogs.add(execLog);
                    log.info("交易命中规则: ruleCode={}, ruleName={}, transactionNo={}",
                            rule.getRuleCode(), rule.getRuleName(), transaction.getTransactionNo());
                }
            } catch (Exception e) {
                log.error("规则执行异常: ruleCode={}, error={}", rule.getRuleCode(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("规则引擎评估完成: transactionId={}, 匹配规则数={}, 耗时={}ms",
                transaction.getId(), matchedLogs.size(), duration);

        return matchedLogs;
    }

    /**
     * 加载所有启用的规则，按优先级排序
     */
    private List<RuleDefinition> loadActiveRules() {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleDefinition::getStatus, "ENABLED")
                .le(RuleDefinition::getEffectiveDate, today)
                .and(w -> w.isNull(RuleDefinition::getExpiryDate)
                        .or()
                        .ge(RuleDefinition::getExpiryDate, today))
                .orderByAsc(RuleDefinition::getPriority);

        return ruleDefinitionMapper.selectList(wrapper);
    }

    /**
     * 执行单条规则评估
     */
    private RuleExecutionLog evaluateRule(RuleDefinition rule, Transaction transaction) {
        long ruleStart = System.currentTimeMillis();

        RuleExecutionLog execLog = new RuleExecutionLog();
        execLog.setRuleId(rule.getId());
        execLog.setRuleCode(rule.getRuleCode());
        execLog.setTransactionId(transaction.getId());
        execLog.setCustomerId(transaction.getCustomerId());
        execLog.setExecutionTime(LocalDateTime.now());
        execLog.setMatchResult(false);
        execLog.setMatchScore(BigDecimal.ZERO);

        try {
            switch (rule.getRuleCategory()) {
                case "THRESHOLD":
                    evaluateThresholdRule(rule, transaction, execLog);
                    break;
                case "LARGE_TXN":
                    evaluateLargeTxnRule(rule, transaction, execLog);
                    break;
                case "VELOCITY":
                    evaluateVelocityRule(rule, transaction, execLog);
                    break;
                case "SUSPICIOUS":
                    evaluateSuspiciousRule(rule, transaction, execLog);
                    break;
                case "CORRELATION":
                    // 关联分析规则，后续P2阶段实现
                    log.debug("关联分析规则暂未实现: ruleCode={}", rule.getRuleCode());
                    break;
                default:
                    log.warn("未知规则类型: {}", rule.getRuleCategory());
            }
        } catch (Exception e) {
            execLog.setExecutionDetail("规则执行异常: " + e.getMessage());
            log.error("规则评估异常: ruleCode={}", rule.getRuleCode(), e);
        }

        // 记录执行耗时
        execLog.setDurationMs(System.currentTimeMillis() - ruleStart);

        // 保存执行日志
        if (execLog.getMatchResult()) {
            ruleExecutionLogMapper.insert(execLog);
            log.debug("规则执行日志已保存: ruleCode={}", rule.getRuleCode());
        }

        return execLog;
    }

    /**
     * 阈值规则评估
     * config_json格式: {"threshold": 50000, "field": "amount", "operator": ">="}
     */
    private void evaluateThresholdRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        try {
            JsonNode config = objectMapper.readTree(rule.getConfigJson());
            BigDecimal threshold = config.has("threshold") ?
                    new BigDecimal(config.get("threshold").asText()) : BigDecimal.ZERO;

            if (transaction.getAmount().compareTo(threshold) >= 0) {
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(100));
                execLog.setExecutionDetail(String.format("交易金额 %s >= 阈值 %s",
                        transaction.getAmount().toPlainString(), threshold.toPlainString()));
            }
        } catch (Exception e) {
            log.error("阈值规则配置解析失败: ruleCode={}", rule.getRuleCode(), e);
            execLog.setExecutionDetail("规则配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 大额交易规则评估
     * config_json格式: {"cash_threshold": 50000, "transfer_threshold": 2000000, "cross_border_threshold": 200000}
     */
    private void evaluateLargeTxnRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        try {
            JsonNode config = objectMapper.readTree(rule.getConfigJson());

            BigDecimal threshold;
            if (Boolean.TRUE.equals(transaction.getIsCrossBorder())) {
                threshold = config.has("cross_border_threshold") ?
                        new BigDecimal(config.get("cross_border_threshold").asText()) : new BigDecimal("200000");
            } else if ("CASH".equals(transaction.getPaymentMethod())) {
                threshold = config.has("cash_threshold") ?
                        new BigDecimal(config.get("cash_threshold").asText()) : new BigDecimal("50000");
            } else {
                threshold = config.has("transfer_threshold") ?
                        new BigDecimal(config.get("transfer_threshold").asText()) : new BigDecimal("2000000");
            }

            if (transaction.getAmount().compareTo(threshold) >= 0) {
                execLog.setMatchResult(true);
                // 计算匹配得分：金额/阈值 * 100，最大100分
                BigDecimal score = transaction.getAmount()
                        .divide(threshold, 2, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .min(BigDecimal.valueOf(100));
                execLog.setMatchScore(score);
                execLog.setExecutionDetail(String.format("大额交易命中: 金额=%s, 阈值=%s, 支付方式=%s, 跨境=%s",
                        transaction.getAmount().toPlainString(), threshold.toPlainString(),
                        transaction.getPaymentMethod(), transaction.getIsCrossBorder()));
            }
        } catch (Exception e) {
            log.error("大额交易规则配置解析失败: ruleCode={}", rule.getRuleCode(), e);
            execLog.setExecutionDetail("规则配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 频率规则评估（占位实现，后续使用Redis统计）
     * config_json格式: {"max_count": 5, "time_window_hours": 24}
     */
    private void evaluateVelocityRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        // TODO: 后续P2阶段使用Redis统计交易频率
        log.debug("频率规则暂为占位实现: ruleCode={}", rule.getRuleCode());
        execLog.setExecutionDetail("频率规则暂为占位实现，后续P2阶段完善");
    }

    /**
     * 可疑交易规则评估（占位实现）
     * config_json格式: {"patterns": ["split_transaction", "unusual_time", "high_risk_country"]}
     */
    private void evaluateSuspiciousRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        // TODO: 后续P2阶段实现可疑交易模式匹配
        log.debug("可疑交易规则暂为占位实现: ruleCode={}", rule.getRuleCode());
        execLog.setExecutionDetail("可疑交易规则暂为占位实现，后续P2阶段完善");
    }
}
