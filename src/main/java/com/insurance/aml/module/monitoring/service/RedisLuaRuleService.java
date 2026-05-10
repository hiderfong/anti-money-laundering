package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Redis Lua 规则服务 - 第三层检测引擎
 * <p>
 * 利用 Redis Lua 脚本的原子性，实现高性能的状态型规则检测：
 * 1. 滑动窗口高频交易检测（ZSET + Lua）
 * 2. 日累计金额统计（HASH + Lua）
 * <p>
 * 与 Drools 和数据库规则并行执行，作为第三层独立检测引擎。
 */
@Slf4j
@Service
@ConditionalOnBean(RedisTemplate.class)
@ConditionalOnProperty(name = "aml.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisLuaRuleService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== Lua 脚本 ====================
    private DefaultRedisScript<List> velocityCheckScript;
    private DefaultRedisScript<List> dailyAccumulateScript;

    // ==================== 配置常量 ====================
    private static final String VELOCITY_KEY_PREFIX = "aml:velocity:";
    private static final String DAILY_KEY_PREFIX = "aml:daily:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 滑动窗口时间窗口(秒) */
    private static final int VELOCITY_WINDOW_SECONDS = 120;
    /** 滑动窗口触发阈值(笔数) */
    private static final int VELOCITY_THRESHOLD = 10;
    /** 日累计金额阈值(元) - 100万 */
    private static final BigDecimal DAILY_AMOUNT_THRESHOLD = new BigDecimal("1000000");
    /** ZSET key TTL: 窗口时间 + 缓冲 */
    private static final int VELOCITY_KEY_TTL = VELOCITY_WINDOW_SECONDS + 60;
    /** 日累计 key TTL: 2天(当天+缓冲) */
    private static final int DAILY_KEY_TTL = 2 * 24 * 3600;

    public RedisLuaRuleService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        velocityCheckScript = new DefaultRedisScript<>();
        velocityCheckScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/velocity_check.lua")));
        velocityCheckScript.setResultType(List.class);

        dailyAccumulateScript = new DefaultRedisScript<>();
        dailyAccumulateScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/daily_accumulate.lua")));
        dailyAccumulateScript.setResultType(List.class);

        log.info("Redis Lua 规则脚本加载完成: velocity_check.lua, daily_accumulate.lua");
    }

    /**
     * 对交易执行所有 Redis Lua 规则检测
     *
     * @param transaction 待评估交易
     * @return 命中的规则执行日志列表
     */
    public List<RuleExecutionLog> evaluate(Transaction transaction) {
        log.info("Redis Lua 规则开始评估: transactionNo={}, customerId={}",
                transaction.getTransactionNo(), transaction.getCustomerId());

        long startTime = System.currentTimeMillis();
        List<RuleExecutionLog> matchedLogs = new ArrayList<>();

        // 规则1: 滑动窗口高频交易检测
        RuleExecutionLog velocityResult = evaluateVelocity(transaction);
        if (velocityResult != null && Boolean.TRUE.equals(velocityResult.getMatchResult())) {
            matchedLogs.add(velocityResult);
        }

        // 规则2: 日累计金额统计
        RuleExecutionLog dailyResult = evaluateDailyAccumulation(transaction);
        if (dailyResult != null && Boolean.TRUE.equals(dailyResult.getMatchResult())) {
            matchedLogs.add(dailyResult);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Redis Lua 规则评估完成: transactionNo={}, 命中规则数={}, 耗时={}ms",
                transaction.getTransactionNo(), matchedLogs.size(), duration);

        return matchedLogs;
    }

    /**
     * 滑动窗口高频交易检测
     * <p>
     * 使用 Redis ZSET 存储交易时间戳，通过 Lua 脚本原子性执行:
     * ZREMRANGEBYSCORE(清理过期) -> ZADD(添加当前) -> ZCARD(统计)
     * <p>
     * 规则: 120秒内同一客户交易 >= 10笔 则触发
     */
    @SuppressWarnings("unchecked")
    private RuleExecutionLog evaluateVelocity(Transaction transaction) {
        long ruleStart = System.currentTimeMillis();
        RuleExecutionLog execLog = buildBaseLog(transaction, "REDIS_VELOCITY",
                "Redis滑动窗口高频交易检测");

        try {
            Long customerId = transaction.getCustomerId();
            String key = VELOCITY_KEY_PREFIX + customerId;

            long nowMillis = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
            long windowStartMillis = nowMillis - (VELOCITY_WINDOW_SECONDS * 1000L);
            String member = transaction.getId() + ":" + UUID.randomUUID().toString().substring(0, 8);

            List<Long> result = redisTemplate.execute(
                    velocityCheckScript,
                    Collections.singletonList(key),
                    String.valueOf(windowStartMillis),
                    String.valueOf(nowMillis),
                    String.valueOf(VELOCITY_THRESHOLD),
                    member,
                    String.valueOf(VELOCITY_KEY_TTL)
            );

            if (result != null && result.size() >= 2) {
                long count = result.get(0);
                long triggered = result.get(1);

                if (triggered == 1) {
                    execLog.setMatchResult(true);
                    execLog.setMatchScore(BigDecimal.valueOf(85));
                    execLog.setExecutionDetail(String.format(
                            "Redis高频交易命中: 客户在%d秒内交易%d笔 >= %d笔阈值, 客户ID=%s, 交易号=%s",
                            VELOCITY_WINDOW_SECONDS, count, VELOCITY_THRESHOLD,
                            customerId, transaction.getTransactionNo()));
                    log.warn("Redis Lua 高频交易触发: customerId={}, count={}, threshold={}",
                            customerId, count, VELOCITY_THRESHOLD);
                } else {
                    execLog.setMatchResult(false);
                    execLog.setMatchScore(BigDecimal.ZERO);
                    execLog.setExecutionDetail(String.format(
                            "Redis频率未达阈值: %d秒内%d笔 < %d笔, 客户ID=%s",
                            VELOCITY_WINDOW_SECONDS, count, VELOCITY_THRESHOLD, customerId));
                }
            } else {
                log.error("Redis Lua velocity脚本返回异常: result={}", result);
                execLog.setExecutionDetail("Redis Lua脚本返回异常");
            }
        } catch (Exception e) {
            log.error("Redis Lua 高频交易检测异常: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
            execLog.setExecutionDetail("Redis Lua 执行异常: " + e.getMessage());
        }

        execLog.setDurationMs(System.currentTimeMillis() - ruleStart);
        return execLog;
    }

    /**
     * 日累计金额统计
     * <p>
     * 使用 Redis HASH 存储每日累计金额和笔数，通过 Lua 脚本原子性执行:
     * HINCRBY(金额累加) -> HINCRBY(笔数累加) -> 判断阈值
     * <p>
     * 规则: 同一客户当日累计交易金额 >= 100万 则触发
     */
    @SuppressWarnings("unchecked")
    private RuleExecutionLog evaluateDailyAccumulation(Transaction transaction) {
        long ruleStart = System.currentTimeMillis();
        RuleExecutionLog execLog = buildBaseLog(transaction, "REDIS_DAILY_AMOUNT",
                "Redis日累计金额统计");

        try {
            Long customerId = transaction.getCustomerId();
            String dateStr = LocalDate.now().format(DATE_FMT);
            String key = DAILY_KEY_PREFIX + customerId + ":" + dateStr;

            // 金额转为分(整数)避免浮点精度问题
            long amountCent = transaction.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            long thresholdCent = DAILY_AMOUNT_THRESHOLD
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            List<Long> result = redisTemplate.execute(
                    dailyAccumulateScript,
                    Collections.singletonList(key),
                    String.valueOf(amountCent),
                    String.valueOf(thresholdCent),
                    String.valueOf(DAILY_KEY_TTL)
            );

            if (result != null && result.size() >= 3) {
                long totalAmountCent = result.get(0);
                long txnCount = result.get(1);
                long triggered = result.get(2);

                BigDecimal totalAmount = BigDecimal.valueOf(totalAmountCent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                if (triggered == 1) {
                    execLog.setMatchResult(true);
                    execLog.setMatchScore(BigDecimal.valueOf(90));
                    execLog.setExecutionDetail(String.format(
                            "Redis日累计金额命中: 客户当日累计交易金额 %s 元 >= %s 元阈值, "
                                    + "当日交易%d笔, 客户ID=%s, 交易号=%s",
                            totalAmount.toPlainString(),
                            DAILY_AMOUNT_THRESHOLD.toPlainString(),
                            txnCount, customerId, transaction.getTransactionNo()));
                    log.warn("Redis Lua 日累计金额触发: customerId={}, totalAmount={}, txnCount={}",
                            customerId, totalAmount, txnCount);
                } else {
                    execLog.setMatchResult(false);
                    execLog.setMatchScore(BigDecimal.ZERO);
                    execLog.setExecutionDetail(String.format(
                            "Redis日累计金额未达阈值: 当日累计 %s 元 < %s 元, 共%d笔, 客户ID=%s",
                            totalAmount.toPlainString(),
                            DAILY_AMOUNT_THRESHOLD.toPlainString(),
                            txnCount, customerId));
                }
            } else {
                log.error("Redis Lua daily脚本返回异常: result={}", result);
                execLog.setExecutionDetail("Redis Lua脚本返回异常");
            }
        } catch (Exception e) {
            log.error("Redis Lua 日累计金额检测异常: transactionNo={}, error={}",
                    transaction.getTransactionNo(), e.getMessage(), e);
            execLog.setExecutionDetail("Redis Lua 执行异常: " + e.getMessage());
        }

        execLog.setDurationMs(System.currentTimeMillis() - ruleStart);
        return execLog;
    }

    /**
     * 构建基础执行日志
     */
    private RuleExecutionLog buildBaseLog(Transaction transaction, String ruleCode, String ruleName) {
        RuleExecutionLog execLog = new RuleExecutionLog();
        execLog.setRuleCode(ruleCode);
        execLog.setTransactionId(transaction.getId());
        execLog.setCustomerId(transaction.getCustomerId());
        execLog.setExecutionTime(LocalDateTime.now());
        execLog.setMatchResult(false);
        execLog.setMatchScore(BigDecimal.ZERO);
        execLog.setDurationMs(0L);
        return execLog;
    }
}
