package com.insurance.aml.module.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.DroolsRuleResult;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionEvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则引擎服务
 * 双引擎架构：同时支持数据库规则和Drools规则引擎
 * - 数据库规则: 通过RuleDefinition表管理，适合简单阈值类规则
 * - Drools规则: 通过.drl文件定义，适合复杂业务逻辑规则
 * 两套规则并行执行，结果合并返回
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleExecutionLogMapper ruleExecutionLogMapper;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;
    private final KieContainer kieContainer;

    /**
     * 高频交易检测默认时间窗口（秒）
     */
    private static final int DEFAULT_VELOCITY_WINDOW_SECONDS = 120;

    /**
     * 高频交易检测默认阈值（笔数）
     */
    private static final int DEFAULT_VELOCITY_THRESHOLD = 10;

    /**
     * 对交易执行所有启用的规则（数据库规则 + Drools规则）
     *
     * @param transaction 待评估的交易
     * @return 匹配到的规则执行日志列表
     */
    public List<RuleExecutionLog> evaluate(Transaction transaction) {
        log.info("规则引擎开始评估: transactionId={}, transactionNo={}", transaction.getId(), transaction.getTransactionNo());

        long startTime = System.currentTimeMillis();
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        // ====== 第一阶段: 数据库规则评估 ======
        List<RuleExecutionLog> dbResults = evaluateDatabaseRules(transaction);
        matchedLogs.addAll(dbResults);

        // ====== 第二阶段: Drools规则评估 ======
        List<RuleExecutionLog> droolsResults = evaluateDroolsRules(transaction);
        matchedLogs.addAll(droolsResults);

        long duration = System.currentTimeMillis() - startTime;
        log.info("规则引擎评估完成: transactionId={}, 数据库规则命中={}, Drools规则命中={}, 总耗时={}ms",
                transaction.getId(), dbResults.size(), droolsResults.size(), duration);

        return matchedLogs;
    }

    /**
     * 仅执行Drools规则评估，返回原始结果对象
     * 适用于需要详细Drools评估信息的场景
     *
     * @param transaction 待评估的交易
     * @return Drools规则评估结果
     */
    public DroolsRuleResult evaluateWithDrools(Transaction transaction) {
        TransactionEvaluationContext context = buildEvaluationContext(transaction);
        DroolsRuleResult result = new DroolsRuleResult();
        result.setTransactionId(transaction.getId());
        result.setTransactionNo(transaction.getTransactionNo());
        result.setCustomerId(transaction.getCustomerId());

        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession();
            kieSession.insert(context);
            kieSession.insert(result);
            kieSession.fireAllRules();
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }

        return result;
    }

    // ====================================================================
    // 数据库规则评估（保留原有逻辑）
    // ====================================================================

    /**
     * 执行数据库规则评估
     */
    private List<RuleExecutionLog> evaluateDatabaseRules(Transaction transaction) {
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        List<RuleDefinition> activeRules = loadActiveRules();
        if (activeRules.isEmpty()) {
            log.debug("无启用的数据库规则，跳过评估");
            return matchedLogs;
        }
        log.debug("加载到 {} 条启用数据库规则", activeRules.size());

        for (RuleDefinition rule : activeRules) {
            try {
                RuleExecutionLog execLog = evaluateRule(rule, transaction);
                if (execLog.getMatchResult()) {
                    matchedLogs.add(execLog);
                    log.info("交易命中数据库规则: ruleCode={}, ruleName={}, transactionNo={}",
                            rule.getRuleCode(), rule.getRuleName(), transaction.getTransactionNo());
                }
            } catch (Exception e) {
                log.error("数据库规则执行异常: ruleCode={}, error={}", rule.getRuleCode(), e.getMessage(), e);
            }
        }

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
     * 执行单条数据库规则评估
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
                        .divide(threshold, 2, RoundingMode.HALF_UP)
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
     * 频率规则评估（基于数据库查询）
     * config_json格式: {"max_count": 10, "time_window_seconds": 120}
     */
    private void evaluateVelocityRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        try {
            JsonNode config = objectMapper.readTree(rule.getConfigJson());
            int maxCount = config.has("max_count") ? config.get("max_count").asInt() : DEFAULT_VELOCITY_THRESHOLD;
            int windowSeconds = config.has("time_window_seconds") ? config.get("time_window_seconds").asInt() : DEFAULT_VELOCITY_WINDOW_SECONDS;

            int recentCount = countRecentTransactions(transaction.getCustomerId(), windowSeconds);

            if (recentCount >= maxCount) {
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(85));
                execLog.setExecutionDetail(String.format("高频交易命中: 客户在%d秒内交易%d笔 >= %d笔阈值, 客户ID=%s",
                        windowSeconds, recentCount, maxCount, transaction.getCustomerId()));
            } else {
                execLog.setExecutionDetail(String.format("频率未达阈值: %d秒内%d笔 < %d笔, 客户ID=%s",
                        windowSeconds, recentCount, maxCount, transaction.getCustomerId()));
            }
        } catch (Exception e) {
            log.error("频率规则配置解析失败: ruleCode={}", rule.getRuleCode(), e);
            execLog.setExecutionDetail("规则配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 可疑交易规则评估
     * config_json格式: {"patterns": ["split_transaction", "unusual_time", "high_risk_country"]}
     */
    private void evaluateSuspiciousRule(RuleDefinition rule, Transaction transaction, RuleExecutionLog execLog) {
        try {
            JsonNode config = objectMapper.readTree(rule.getConfigJson());
            if (!config.has("patterns")) {
                execLog.setExecutionDetail("可疑规则缺少patterns配置");
                return;
            }

            List<String> matchedPatterns = new ArrayList<>();
            JsonNode patterns = config.get("patterns");

            for (JsonNode pattern : patterns) {
                String patternType = pattern.asText();
                switch (patternType) {
                    case "split_transaction":
                        if (checkSplitTransaction(transaction)) {
                            matchedPatterns.add("拆分交易");
                        }
                        break;
                    case "unusual_time":
                        if (checkUnusualTime(transaction)) {
                            matchedPatterns.add("异常时间交易");
                        }
                        break;
                    case "high_risk_country":
                        if (Boolean.TRUE.equals(transaction.getIsCrossBorder())) {
                            matchedPatterns.add("高风险国家跨境交易");
                        }
                        break;
                    default:
                        log.debug("未知可疑交易模式: {}", patternType);
                }
            }

            if (!matchedPatterns.isEmpty()) {
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(matchedPatterns.size() * 30).min(BigDecimal.valueOf(100)));
                execLog.setExecutionDetail(String.format("可疑交易命中: %s, 交易流水=%s",
                        String.join(", ", matchedPatterns), transaction.getTransactionNo()));
            }
        } catch (Exception e) {
            log.error("可疑交易规则配置解析失败: ruleCode={}", rule.getRuleCode(), e);
            execLog.setExecutionDetail("规则配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 检查拆分交易：同客户短时间内多笔金额接近的交易
     */
    private boolean checkSplitTransaction(Transaction transaction) {
        if (transaction.getAmount().compareTo(new BigDecimal("50000")) < 0) {
            return false;
        }
        int recentCount = countRecentTransactions(transaction.getCustomerId(), 3600);
        return recentCount >= 3;
    }

    /**
     * 检查异常时间交易：非工作时间（0:00-6:00）的大额交易
     */
    private boolean checkUnusualTime(Transaction transaction) {
        if (transaction.getTransactionTime() == null) {
            return false;
        }
        int hour = transaction.getTransactionTime().getHour();
        return (hour >= 0 && hour < 6) && transaction.getAmount().compareTo(new BigDecimal("10000")) >= 0;
    }

    // ====================================================================
    // Drools规则评估
    // ====================================================================

    /**
     * 执行Drools规则评估，将结果转换为RuleExecutionLog格式
     */
    private List<RuleExecutionLog> evaluateDroolsRules(Transaction transaction) {
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        try {
            DroolsRuleResult droolsResult = evaluateWithDrools(transaction);

            if (!droolsResult.hasMatch()) {
                log.debug("Drools规则未命中: transactionNo={}", transaction.getTransactionNo());
                return matchedLogs;
            }

            log.info("Drools规则命中: transactionNo={}, 命中规则数={}",
                    transaction.getTransactionNo(), droolsResult.getMatchedRuleCodes().size());

            // 将Drools结果转换为RuleExecutionLog
            for (int i = 0; i < droolsResult.getMatchedRuleCodes().size(); i++) {
                String ruleCode = droolsResult.getMatchedRuleCodes().get(i);
                String ruleName = droolsResult.getMatchedRuleNames().get(i);
                String detail = droolsResult.getEvaluationDetails().get(i);

                RuleExecutionLog execLog = new RuleExecutionLog();
                execLog.setRuleCode(ruleCode);
                execLog.setTransactionId(transaction.getId());
                execLog.setCustomerId(transaction.getCustomerId());
                execLog.setExecutionTime(LocalDateTime.now());
                execLog.setMatchResult(true);
                execLog.setMatchScore(droolsResult.getTotalRiskScore().min(BigDecimal.valueOf(100)));
                execLog.setExecutionDetail(detail);
                execLog.setDurationMs(0L);

                // 尝试查找对应的数据库规则定义以关联ruleId
                LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(RuleDefinition::getRuleCode, ruleCode);
                RuleDefinition dbRule = ruleDefinitionMapper.selectOne(wrapper);
                if (dbRule != null) {
                    execLog.setRuleId(dbRule.getId());
                }

                // 保存执行日志
                ruleExecutionLogMapper.insert(execLog);
                matchedLogs.add(execLog);
            }
        } catch (Exception e) {
            log.error("Drools规则评估异常: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
        }

        return matchedLogs;
    }

    // ====================================================================
    // 辅助方法
    // ====================================================================

    /**
     * 构建交易评估上下文，注入统计类数据
     */
    private TransactionEvaluationContext buildEvaluationContext(Transaction transaction) {
        TransactionEvaluationContext context = new TransactionEvaluationContext();
        context.setTransaction(transaction);
        context.setRecentTimeWindowSeconds(DEFAULT_VELOCITY_WINDOW_SECONDS);

        // 查询最近时间窗口内的交易笔数
        int recentCount = countRecentTransactions(transaction.getCustomerId(), DEFAULT_VELOCITY_WINDOW_SECONDS);
        context.setRecentTransactionCount(recentCount);

        // 查询当日累计交易信息
        BigDecimal dailyAmount = sumDailyAmount(transaction.getCustomerId());
        int dailyCount = countDailyTransactions(transaction.getCustomerId());
        context.setDailyCumulativeAmount(dailyAmount);
        context.setDailyTransactionCount(dailyCount);

        return context;
    }

    /**
     * 统计客户最近N秒内的交易笔数
     */
    private int countRecentTransactions(Long customerId, int windowSeconds) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSeconds);
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getCustomerId, customerId)
                .ge(Transaction::getTransactionTime, since)
                .eq(Transaction::getStatus, "SUCCESS");
        Long count = transactionMapper.selectCount(wrapper);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 统计客户当日累计交易金额
     */
    private BigDecimal sumDailyAmount(Long customerId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getCustomerId, customerId)
                .ge(Transaction::getTransactionTime, dayStart)
                .lt(Transaction::getTransactionTime, dayEnd)
                .eq(Transaction::getStatus, "SUCCESS")
                .select(Transaction::getAmount);

        List<Transaction> transactions = transactionMapper.selectList(wrapper);
        return transactions.stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 统计客户当日交易笔数
     */
    private int countDailyTransactions(Long customerId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();

        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Transaction::getCustomerId, customerId)
                .ge(Transaction::getTransactionTime, dayStart)
                .lt(Transaction::getTransactionTime, dayEnd)
                .eq(Transaction::getStatus, "SUCCESS");
        Long count = transactionMapper.selectCount(wrapper);
        return count != null ? count.intValue() : 0;
    }
}
