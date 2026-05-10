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
import com.insurance.aml.common.enums.TransactionStatus;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionEvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 规则引擎服务
 * 四引擎架构：数据库规则 + Drools规则 + Redis Lua规则 + ML异常检测 四路并行评估
 * - 数据库规则: 通过RuleDefinition表管理，适合简单阈值类规则
 * - Drools规则: 通过.drl文件定义，适合复杂业务逻辑规则
 * - Redis Lua规则: 基于Lua脚本的高性能实时规则，适合高频计数/窗口类规则
 * - ML异常检测: 基于Isolation Forest的机器学习异常检测，发现隐匿模式
 *
 * 异步处理：四种规则引擎通过CompletableFuture并行执行，allOf等待全部完成后合并结果
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
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Executor amlTaskExecutor;
    private final TransactionAnomalyDetector anomalyDetector;

    /**
     * 高频交易检测默认时间窗口（秒）
     */
    private static final int DEFAULT_VELOCITY_WINDOW_SECONDS = 120;

    /**
     * 高频交易检测默认阈值（笔数）
     */
    private static final int DEFAULT_VELOCITY_THRESHOLD = 10;

    /** Redis Lua脚本Key前缀 - 滑动窗口计数 */
    private static final String LUA_VELOCITY_KEY_PREFIX = "aml:rule:velocity:";
    /** Redis Lua脚本Key前缀 - 单日累计金额 */
    private static final String LUA_DAILY_AMOUNT_KEY_PREFIX = "aml:rule:daily:amount:";
    /** Redis Lua脚本Key前缀 - 单日交易笔数 */
    private static final String LUA_DAILY_COUNT_KEY_PREFIX = "aml:rule:daily:count:";

    /**
     * 同步评估（保留兼容性）
     * 四种引擎串行执行
     */
    public List<RuleExecutionLog> evaluate(Transaction transaction) {
        log.info("规则引擎开始评估(同步): transactionId={}, transactionNo={}", transaction.getId(), transaction.getTransactionNo());

        long startTime = System.currentTimeMillis();
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        // 第一阶段: 数据库规则评估
        List<RuleExecutionLog> dbResults = evaluateDatabaseRules(transaction);
        matchedLogs.addAll(dbResults);

        // 第二阶段: Drools规则评估
        List<RuleExecutionLog> droolsResults = evaluateDroolsRules(transaction);
        matchedLogs.addAll(droolsResults);

        // 第三阶段: Redis Lua规则评估
        List<RuleExecutionLog> redisResults = evaluateRedisLuaRules(transaction);
        matchedLogs.addAll(redisResults);

        // 第四阶段: ML异常检测评估
        List<RuleExecutionLog> mlResults = evaluateMLAnomaly(transaction);
        matchedLogs.addAll(mlResults);

        long duration = System.currentTimeMillis() - startTime;
        log.info("规则引擎评估完成(同步): transactionId={}, DB规则命中={}, Drools命中={}, Redis命中={}, ML命中={}, 总耗时={}ms",
                transaction.getId(), dbResults.size(), droolsResults.size(), redisResults.size(), mlResults.size(), duration);

        return matchedLogs;
    }

    /**
     * 异步并行评估 - 四路规则引擎并行执行
     *
     * 流程：
     *   1) 四个CompletableFuture分别执行：数据库规则、Drools规则、Redis Lua规则、ML异常检测
     *   2) CompletableFuture.allOf 等待全部完成
     *   3) 合并四路结果返回
     *
     * @param transaction 待评估的交易
     * @return CompletableFuture，包含所有命中的规则执行日志
     */
    public CompletableFuture<List<RuleExecutionLog>> evaluateAsync(Transaction transaction) {
        log.info("[异步规则引擎] 开始并行评估: transactionId={}, transactionNo={}",
                transaction.getId(), transaction.getTransactionNo());

        long startTime = System.currentTimeMillis();

        // ===== 路径1: 数据库规则评估（异步） =====
        CompletableFuture<List<RuleExecutionLog>> dbFuture = CompletableFuture.supplyAsync(
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        List<RuleExecutionLog> results = evaluateDatabaseRules(transaction);
                        log.debug("[异步规则引擎] 数据库规则完成: 命中={}, 耗时={}ms",
                                results.size(), System.currentTimeMillis() - start);
                        return results;
                    } catch (Exception e) {
                        log.error("[异步规则引擎] 数据库规则评估异常: transactionNo={}, error={}",
                                transaction.getTransactionNo(), e.getMessage(), e);
                        return new ArrayList<RuleExecutionLog>();
                    }
                },
                amlTaskExecutor
        );

        // ===== 路径2: Drools规则评估（异步） =====
        CompletableFuture<List<RuleExecutionLog>> droolsFuture = CompletableFuture.supplyAsync(
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        List<RuleExecutionLog> results = evaluateDroolsRules(transaction);
                        log.debug("[异步规则引擎] Drools规则完成: 命中={}, 耗时={}ms",
                                results.size(), System.currentTimeMillis() - start);
                        return results;
                    } catch (Exception e) {
                        log.error("[异步规则引擎] Drools规则评估异常: transactionNo={}, error={}",
                                transaction.getTransactionNo(), e.getMessage(), e);
                        return new ArrayList<RuleExecutionLog>();
                    }
                },
                amlTaskExecutor
        );

        // ===== 路径3: Redis Lua规则评估（异步） =====
        CompletableFuture<List<RuleExecutionLog>> redisFuture = CompletableFuture.supplyAsync(
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        List<RuleExecutionLog> results = evaluateRedisLuaRules(transaction);
                        log.debug("[异步规则引擎] Redis Lua规则完成: 命中={}, 耗时={}ms",
                                results.size(), System.currentTimeMillis() - start);
                        return results;
                    } catch (Exception e) {
                        log.error("[异步规则引擎] Redis Lua规则评估异常: transactionNo={}, error={}",
                                transaction.getTransactionNo(), e.getMessage(), e);
                        return new ArrayList<RuleExecutionLog>();
                    }
                },
                amlTaskExecutor
        );

        // ===== 路径4: ML异常检测评估（异步） =====
        CompletableFuture<List<RuleExecutionLog>> mlFuture = CompletableFuture.supplyAsync(
                () -> {
                    long start = System.currentTimeMillis();
                    try {
                        List<RuleExecutionLog> results = evaluateMLAnomaly(transaction);
                        log.debug("[异步规则引擎] ML异常检测完成: 命中={}, 耗时={}ms",
                                results.size(), System.currentTimeMillis() - start);
                        return results;
                    } catch (Exception e) {
                        log.error("[异步规则引擎] ML异常检测评估异常: transactionNo={}, error={}",
                                transaction.getTransactionNo(), e.getMessage(), e);
                        return new ArrayList<RuleExecutionLog>();
                    }
                },
                amlTaskExecutor
        );

        // ===== 等待全部完成，合并结果 =====
        return CompletableFuture.allOf(dbFuture, droolsFuture, redisFuture, mlFuture)
                .thenApply(v -> {
                    List<RuleExecutionLog> allMatched = new ArrayList<>();
                    allMatched.addAll(dbFuture.join());
                    allMatched.addAll(droolsFuture.join());
                    allMatched.addAll(redisFuture.join());
                    allMatched.addAll(mlFuture.join());

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[异步规则引擎] 并行评估完成: transactionId={}, DB命中={}, Drools命中={}, Redis命中={}, ML命中={}, 总耗时={}ms",
                            transaction.getId(),
                            dbFuture.join().size(), droolsFuture.join().size(),
                            redisFuture.join().size(), mlFuture.join().size(),
                            duration);

                    return allMatched;
                })
                .exceptionally(ex -> {
                    log.error("[异步规则引擎] 并行评估部分异常: transactionNo={}, error={}",
                            transaction.getTransactionNo(), ex.getMessage(), ex);
                    // 尝试收集已完成的结果
                    List<RuleExecutionLog> partial = new ArrayList<>();
                    try { partial.addAll(dbFuture.join()); } catch (Exception ignored) {}
                    try { partial.addAll(droolsFuture.join()); } catch (Exception ignored) {}
                    try { partial.addAll(redisFuture.join()); } catch (Exception ignored) {}
                    try { partial.addAll(mlFuture.join()); } catch (Exception ignored) {}
                    return partial;
                });
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
    // Redis Lua规则评估（新增第三引擎）
    // ====================================================================

    /**
     * 执行Redis Lua规则评估
     *
     * 基于Redis Lua脚本的高性能实时规则，利用Redis原子性操作实现：
     * - 滑动窗口频率检测（ZSET + Lua）
     * - 单日累计金额/笔数阈值检测（INCRBYFLOAT + Lua）
     * - 复合条件组合规则
     *
     * 优势：Redis单线程保证原子性，Lua脚本减少网络往返，适合高频交易场景
     */
    private List<RuleExecutionLog> evaluateRedisLuaRules(Transaction transaction) {
        if (redisTemplateProvider.getIfAvailable() == null) {
            log.debug("Redis未启用，跳过Redis Lua规则评估: transactionNo={}", transaction.getTransactionNo());
            return Collections.emptyList();
        }

        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        try {
            // 规则1: 滑动窗口频率检测（Lua脚本原子操作）
            RuleExecutionLog velocityResult = evaluateRedisVelocityRule(transaction);
            if (velocityResult != null && velocityResult.getMatchResult()) {
                matchedLogs.add(velocityResult);
                ruleExecutionLogMapper.insert(velocityResult);
            }

            // 规则2: 单日累计金额阈值检测
            RuleExecutionLog dailyAmountResult = evaluateRedisDailyAmountRule(transaction);
            if (dailyAmountResult != null && dailyAmountResult.getMatchResult()) {
                matchedLogs.add(dailyAmountResult);
                ruleExecutionLogMapper.insert(dailyAmountResult);
            }

            // 规则3: 单日交易笔数阈值检测
            RuleExecutionLog dailyCountResult = evaluateRedisDailyCountRule(transaction);
            if (dailyCountResult != null && dailyCountResult.getMatchResult()) {
                matchedLogs.add(dailyCountResult);
                ruleExecutionLogMapper.insert(dailyCountResult);
            }

            log.debug("Redis Lua规则评估完成: transactionNo={}, 命中={}条",
                    transaction.getTransactionNo(), matchedLogs.size());

        } catch (Exception e) {
            log.error("Redis Lua规则评估异常: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
        }

        return matchedLogs;
    }

    /**
     * Redis滑动窗口频率检测（Lua脚本实现）
     *
     * Lua脚本逻辑：
     *   1. 向ZSET添加当前交易（score=时间戳）
     *   2. 移除窗口外的旧记录
     *   3. 返回窗口内的交易笔数
     *   4. 原子性操作，避免并发问题
     */
    private RuleExecutionLog evaluateRedisVelocityRule(Transaction transaction) {
        long ruleStart = System.currentTimeMillis();
        String customerId = String.valueOf(transaction.getCustomerId());
        int windowSeconds = DEFAULT_VELOCITY_WINDOW_SECONDS;
        int maxCount = DEFAULT_VELOCITY_THRESHOLD;

        // 滑动窗口计数Lua脚本
        String luaScript =
                "local key = KEYS[1] " +
                "local now = tonumber(ARGV[1]) " +
                "local window = tonumber(ARGV[2]) " +
                "local member = ARGV[3] " +
                "redis.call('ZADD', key, now, member) " +
                "redis.call('ZREMRANGEBYSCORE', key, 0, now - window) " +
                "local count = redis.call('ZCARD', key) " +
                "redis.call('EXPIRE', key, window) " +
                "return count";

        String redisKey = LUA_VELOCITY_KEY_PREFIX + customerId;
        long nowMs = System.currentTimeMillis();
        String member = transaction.getTransactionNo() + ":" + nowMs;

        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return null;
            }
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
            Long count = redisTemplate.execute(script,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowSeconds * 1000L),
                    member);

            int recentCount = count != null ? count.intValue() : 0;

            RuleExecutionLog execLog = new RuleExecutionLog();
            execLog.setRuleCode("REDIS_VELOCITY");
            execLog.setTransactionId(transaction.getId());
            execLog.setCustomerId(transaction.getCustomerId());
            execLog.setExecutionTime(LocalDateTime.now());
            execLog.setDurationMs(System.currentTimeMillis() - ruleStart);

            if (recentCount >= maxCount) {
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(85));
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 高频交易命中: 客户在%d秒窗口内交易%d笔 >= %d笔阈值, 客户ID=%s",
                        windowSeconds, recentCount, maxCount, customerId));
                log.info("Redis Lua频率规则命中: customerId={}, count={}/{}", customerId, recentCount, maxCount);
            } else {
                execLog.setMatchResult(false);
                execLog.setMatchScore(BigDecimal.ZERO);
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 频率未达阈值: %d秒窗口内%d笔 < %d笔",
                        windowSeconds, recentCount, maxCount));
            }

            return execLog;
        } catch (Exception e) {
            log.error("Redis Lua滑动窗口频率检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Redis单日累计金额阈值检测
     *
     * 使用Redis INCRBYFLOAT + Lua脚本实现原子性的金额累加与阈值判断
     * 阈值: 现金50万 / 转账200万 / 跨境20万
     */
    private RuleExecutionLog evaluateRedisDailyAmountRule(Transaction transaction) {
        long ruleStart = System.currentTimeMillis();
        String customerId = String.valueOf(transaction.getCustomerId());
        String today = LocalDate.now().toString();

        // Lua脚本：累加金额并返回新总额
        String luaScript =
                "local key = KEYS[1] " +
                "local amount = tonumber(ARGV[1]) " +
                "local ttl = tonumber(ARGV[2]) " +
                "local current = redis.call('INCRBYFLOAT', key, amount) " +
                "if redis.call('TTL', key) == -1 then " +
                "  redis.call('EXPIRE', key, ttl) " +
                "end " +
                "return current";

        String redisKey = LUA_DAILY_AMOUNT_KEY_PREFIX + customerId + ":" + today;

        // 确定阈值
        BigDecimal threshold;
        if (Boolean.TRUE.equals(transaction.getIsCrossBorder())) {
            threshold = new BigDecimal("200000");
        } else if ("CASH".equals(transaction.getPaymentMethod())) {
            threshold = new BigDecimal("500000");
        } else {
            threshold = new BigDecimal("2000000");
        }

        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return null;
            }
            DefaultRedisScript<String> script = new DefaultRedisScript<>(luaScript, String.class);
            String result = redisTemplate.execute(script,
                    Collections.singletonList(redisKey),
                    transaction.getAmount().toPlainString(),
                    String.valueOf(86400));  // 24小时过期

            BigDecimal dailyTotal = result != null ? new BigDecimal(result) : transaction.getAmount();

            RuleExecutionLog execLog = new RuleExecutionLog();
            execLog.setRuleCode("REDIS_DAILY_AMOUNT");
            execLog.setTransactionId(transaction.getId());
            execLog.setCustomerId(transaction.getCustomerId());
            execLog.setExecutionTime(LocalDateTime.now());
            execLog.setDurationMs(System.currentTimeMillis() - ruleStart);

            if (dailyTotal.compareTo(threshold) >= 0) {
                execLog.setMatchResult(true);
                BigDecimal score = dailyTotal
                        .divide(threshold, 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .min(BigDecimal.valueOf(100));
                execLog.setMatchScore(score);
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 单日累计金额命中: 日累计=%s >= 阈值=%s, 客户ID=%s",
                        dailyTotal.toPlainString(), threshold.toPlainString(), customerId));
                log.info("Redis Lua日累计金额规则命中: customerId={}, dailyTotal={}/{}", customerId, dailyTotal, threshold);
            } else {
                execLog.setMatchResult(false);
                execLog.setMatchScore(BigDecimal.ZERO);
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 日累计金额未达阈值: %s < %s",
                        dailyTotal.toPlainString(), threshold.toPlainString()));
            }

            return execLog;
        } catch (Exception e) {
            log.error("Redis Lua日累计金额检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Redis单日交易笔数阈值检测
     *
     * 使用Redis INCR + Lua脚本实现原子性的笔数累加与阈值判断
     * 阈值: 单日交易笔数超过50笔触发预警
     */
    private RuleExecutionLog evaluateRedisDailyCountRule(Transaction transaction) {
        long ruleStart = System.currentTimeMillis();
        String customerId = String.valueOf(transaction.getCustomerId());
        String today = LocalDate.now().toString();
        int dailyCountThreshold = 50;

        // Lua脚本：累加笔数并返回新总数
        String luaScript =
                "local key = KEYS[1] " +
                "local ttl = tonumber(ARGV[1]) " +
                "local count = redis.call('INCR', key) " +
                "if count == 1 then " +
                "  redis.call('EXPIRE', key, ttl) " +
                "end " +
                "return count";

        String redisKey = LUA_DAILY_COUNT_KEY_PREFIX + customerId + ":" + today;

        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return null;
            }
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
            Long result = redisTemplate.execute(script,
                    Collections.singletonList(redisKey),
                    String.valueOf(86400));  // 24小时过期

            int dailyCount = result != null ? result.intValue() : 1;

            RuleExecutionLog execLog = new RuleExecutionLog();
            execLog.setRuleCode("REDIS_DAILY_COUNT");
            execLog.setTransactionId(transaction.getId());
            execLog.setCustomerId(transaction.getCustomerId());
            execLog.setExecutionTime(LocalDateTime.now());
            execLog.setDurationMs(System.currentTimeMillis() - ruleStart);

            if (dailyCount >= dailyCountThreshold) {
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(70));
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 单日交易笔数命中: 日交易%d笔 >= %d笔阈值, 客户ID=%s",
                        dailyCount, dailyCountThreshold, customerId));
                log.info("Redis Lua日交易笔数规则命中: customerId={}, dailyCount={}/{}", customerId, dailyCount, dailyCountThreshold);
            } else {
                execLog.setMatchResult(false);
                execLog.setMatchScore(BigDecimal.ZERO);
                execLog.setExecutionDetail(String.format(
                        "[Redis Lua] 日交易笔数未达阈值: %d笔 < %d笔",
                        dailyCount, dailyCountThreshold));
            }

            return execLog;
        } catch (Exception e) {
            log.error("Redis Lua日交易笔数检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return null;
        }
    }

    // ====================================================================
    // ML异常检测评估（第四引擎）
    // ====================================================================

    /**
     * 执行ML异常检测评估
     *
     * 基于Isolation Forest模型对交易进行异常评分：
     * - 调用TransactionAnomalyDetector获取异常分数 [0.0, 1.0]
     * - 异常分数 >= 阈值(默认0.7)时生成RuleExecutionLog
     * - 根据分数确定风险等级：
     *   >= 0.85 : 高危(score=95)
     *   >= 0.70 : 中危(score=75)
     *   < 0.70  : 正常(不命中)
     *
     * @param transaction 待评估的交易
     * @return 命中时返回包含1条记录的列表，未命中返回空列表
     */
    private List<RuleExecutionLog> evaluateMLAnomaly(Transaction transaction) {
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();
        long ruleStart = System.currentTimeMillis();

        try {
            double anomalyScore = anomalyDetector.predict(transaction);

            RuleExecutionLog execLog = new RuleExecutionLog();
            execLog.setRuleCode("ML_ISOLATION_FOREST");
            execLog.setTransactionId(transaction.getId());
            execLog.setCustomerId(transaction.getCustomerId());
            execLog.setExecutionTime(LocalDateTime.now());

            // 异常分数阈值判断
            if (anomalyScore >= 0.85) {
                // 高危异常
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(95));
                execLog.setExecutionDetail(String.format(
                        "[ML] Isolation Forest高危异常: 异常分数=%.4f (>=0.85), 交易金额=%s, 跨境=%s, 交易时间=%s, 流水号=%s",
                        anomalyScore,
                        transaction.getAmount().toPlainString(),
                        transaction.getIsCrossBorder(),
                        transaction.getTransactionTime(),
                        transaction.getTransactionNo()));
                matchedLogs.add(execLog);
                log.info("[ML] 高危异常检测命中: transactionNo={}, score={}", transaction.getTransactionNo(), anomalyScore);

            } else if (anomalyScore >= 0.70) {
                // 中危异常
                execLog.setMatchResult(true);
                execLog.setMatchScore(BigDecimal.valueOf(75));
                execLog.setExecutionDetail(String.format(
                        "[ML] Isolation Forest中危异常: 异常分数=%.4f (>=0.70), 交易金额=%s, 跨境=%s, 交易时间=%s, 流水号=%s",
                        anomalyScore,
                        transaction.getAmount().toPlainString(),
                        transaction.getIsCrossBorder(),
                        transaction.getTransactionTime(),
                        transaction.getTransactionNo()));
                matchedLogs.add(execLog);
                log.info("[ML] 中危异常检测命中: transactionNo={}, score={}", transaction.getTransactionNo(), anomalyScore);

            } else {
                execLog.setMatchResult(false);
                execLog.setMatchScore(BigDecimal.valueOf(anomalyScore * 100).setScale(2, java.math.RoundingMode.HALF_UP));
                log.debug("[ML] 交易正常: transactionNo={}, score={}", transaction.getTransactionNo(), anomalyScore);
            }

            execLog.setDurationMs(System.currentTimeMillis() - ruleStart);

            // 仅命中时保存执行日志
            if (execLog.getMatchResult()) {
                // 尝试查找ML规则定义以关联ruleId
                LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(RuleDefinition::getRuleCode, "ML_ISOLATION_FOREST");
                RuleDefinition mlRule = ruleDefinitionMapper.selectOne(wrapper);
                if (mlRule != null) {
                    execLog.setRuleId(mlRule.getId());
                }
                ruleExecutionLogMapper.insert(execLog);
            }

        } catch (Exception e) {
            log.error("[ML] 异常检测评估失败: transactionNo={}, error={}",
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
                .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode());
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
                .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode())
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
                .eq(Transaction::getStatus, TransactionStatus.SUCCESS.getCode());
        Long count = transactionMapper.selectCount(wrapper);
        return count != null ? count.intValue() : 0;
    }
}
