package com.insurance.aml.module.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.kie.api.runtime.KieContainer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 规则引擎服务单元测试
 * 覆盖 evaluate / evaluateAsync 方法的多种场景
 * 四引擎：数据库规则 + Drools规则 + Redis Lua规则 + ML异常检测
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("规则引擎服务测试")
class RuleEngineServiceTest {

    @Mock
    RuleDefinitionMapper ruleDefinitionMapper;

    @Mock
    RuleExecutionLogMapper ruleExecutionLogMapper;

    @Mock
    TransactionMapper transactionMapper;

    @Mock
    KieContainer kieContainer;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    Executor amlTaskExecutor;

    @Mock
    TransactionAnomalyDetector anomalyDetector;

    /** 使用真实的ObjectMapper以便解析JSON配置 */
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    RuleEngineService ruleEngineService;

    /**
     * 创建测试用交易对象
     */
    private Transaction createTestTransaction(Long id, BigDecimal amount) {
        Transaction txn = new Transaction();
        txn.setId(id);
        txn.setTransactionNo("TXN_TEST_" + id);
        txn.setCustomerId(100L);
        txn.setAmount(amount);
        txn.setPaymentMethod("TRANSFER");
        txn.setIsCrossBorder(false);
        txn.setTransactionTime(LocalDateTime.now());
        return txn;
    }

    /**
     * 创建阈值规则定义
     */
    private RuleDefinition createThresholdRule(String ruleCode, long ruleId, int threshold) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(ruleId);
        rule.setRuleCode(ruleCode);
        rule.setRuleName("测试阈值规则-" + ruleCode);
        rule.setRuleCategory("THRESHOLD");
        rule.setStatus("ENABLED");
        rule.setPriority(1);
        rule.setEffectiveDate(LocalDate.now().minusDays(1));
        rule.setExpiryDate(LocalDate.now().plusYears(1));
        rule.setConfigJson("{\"threshold\": " + threshold + ", \"field\": \"amount\", \"operator\": \">=\"}");
        return rule;
    }

    /**
     * 创建大额交易规则定义
     */
    private RuleDefinition createLargeTxnRule(String ruleCode, long ruleId) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(ruleId);
        rule.setRuleCode(ruleCode);
        rule.setRuleName("测试大额交易规则-" + ruleCode);
        rule.setRuleCategory("LARGE_TXN");
        rule.setStatus("ENABLED");
        rule.setPriority(2);
        rule.setEffectiveDate(LocalDate.now().minusDays(1));
        rule.setConfigJson("{\"cash_threshold\": 50000, \"transfer_threshold\": 2000000, \"cross_border_threshold\": 200000}");
        return rule;
    }

    @BeforeEach
    void setUp() {
        // Mock Redis template (用于Redis Lua引擎)
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(null);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(null);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(null);

        // Mock ML异常检测器 (默认返回正常分数)
        lenient().when(anomalyDetector.predict(any(Transaction.class))).thenReturn(0.3);
    }

    /**
     * 测试阈值规则匹配：交易金额超过规则配置的阈值
     */
    @Test
    @DisplayName("阈值规则匹配 -> 交易金额15000超过阈值10000，返回1条命中")
    void evaluate_thresholdRuleMatch() {
        // 准备规则：阈值=10000
        RuleDefinition thresholdRule = createThresholdRule("RULE_001", 1L, 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        // 准备交易：金额=15000
        Transaction txn = createTestTransaction(1L, new BigDecimal("15000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：应命中1条数据库规则
        assertNotNull(results, "结果不应为空");
        assertTrue(results.size() >= 1, "应至少命中1条规则");

        // 验证数据库规则命中
        RuleExecutionLog dbHit = results.stream()
                .filter(r -> "RULE_001".equals(r.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(dbHit, "应命中RULE_001规则");
        assertTrue(dbHit.getMatchResult(), "规则应被命中");
        assertTrue(dbHit.getMatchScore().compareTo(BigDecimal.ZERO) > 0, "匹配得分应大于0");
    }

    /**
     * 测试阈值规则不匹配：交易金额低于规则配置的阈值
     */
    @Test
    @DisplayName("阈值规则不匹配 -> 交易金额5000低于阈值10000，数据库规则不命中")
    void evaluate_thresholdRuleNoMatch() {
        // 准备规则：阈值=10000
        RuleDefinition thresholdRule = createThresholdRule("RULE_001", 1L, 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));

        // 准备交易：金额=5000
        Transaction txn = createTestTransaction(2L, new BigDecimal("5000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：数据库规则不应命中
        assertNotNull(results, "结果不应为空");
        boolean dbRuleHit = results.stream().anyMatch(r -> "RULE_001".equals(r.getRuleCode()));
        assertFalse(dbRuleHit, "交易金额5000未超过阈值10000，不应命中规则");
    }

    /**
     * 测试无启用规则的场景
     */
    @Test
    @DisplayName("无启用规则 -> 数据库规则返回空列表")
    void evaluate_noActiveRules() {
        // mock返回空规则列表
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 准备交易
        Transaction txn = createTestTransaction(3L, new BigDecimal("50000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：数据库规则部分应为空（其他引擎可能返回结果）
        assertNotNull(results, "结果不应为空");
        boolean dbRuleHit = results.stream().anyMatch(r ->
                r.getRuleCode() != null && r.getRuleCode().startsWith("RULE_"));
        assertFalse(dbRuleHit, "无启用数据库规则时不应命中");
    }

    /**
     * 测试多条规则同时评估
     */
    @Test
    @DisplayName("多规则评估 -> 3条阈值规则全部命中")
    void evaluate_multipleRules() {
        // 准备3条阈值规则，阈值均小于交易金额
        RuleDefinition rule1 = createThresholdRule("RULE_001", 1L, 5000);
        rule1.setPriority(1);
        RuleDefinition rule2 = createThresholdRule("RULE_002", 2L, 10000);
        rule2.setPriority(2);
        RuleDefinition rule3 = createThresholdRule("RULE_003", 3L, 20000);
        rule3.setPriority(3);

        when(ruleDefinitionMapper.selectList(any())).thenReturn(Arrays.asList(rule1, rule2, rule3));
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        // 准备交易：金额=25000，超过所有规则阈值
        Transaction txn = createTestTransaction(4L, new BigDecimal("25000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：3条数据库规则应全部命中
        assertNotNull(results, "结果不应为空");
        long dbHitCount = results.stream()
                .filter(r -> r.getRuleCode() != null && r.getRuleCode().startsWith("RULE_"))
                .filter(RuleExecutionLog::getMatchResult)
                .count();
        assertEquals(3, dbHitCount, "3条阈值规则应全部命中");
    }

    /**
     * 测试异步并行评估
     */
    @Test
    @DisplayName("异步并行评估 -> 返回CompletableFuture且包含数据库规则命中")
    void evaluateAsync_returnsFuture() throws Exception {
        // 准备
        RuleDefinition thresholdRule = createThresholdRule("RULE_ASYNC", 10L, 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        // Mock异步执行器：直接在当前线程执行
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(amlTaskExecutor).execute(any(Runnable.class));

        Transaction txn = createTestTransaction(5L, new BigDecimal("50000"));

        // 执行
        CompletableFuture<List<RuleExecutionLog>> future = ruleEngineService.evaluateAsync(txn);

        // 验证
        assertNotNull(future, "Future不应为空");
        List<RuleExecutionLog> results = future.get();
        assertNotNull(results, "异步结果不应为空");
        assertTrue(results.size() >= 1, "应至少命中1条规则");
    }

    /**
     * 测试大额交易规则 - 现金交易
     */
    @Test
    @DisplayName("大额交易规则-现金 -> 金额60000超过现金阈值50000，应命中")
    void evaluate_largeTxnCashRule_match() {
        // 准备大额交易规则
        RuleDefinition largeTxnRule = createLargeTxnRule("LARGE_TXN_001", 5L);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(largeTxnRule));
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        // 准备现金交易
        Transaction txn = createTestTransaction(6L, new BigDecimal("60000"));
        txn.setPaymentMethod("CASH");

        // 执行
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：应命中大额交易规则
        RuleExecutionLog largeHit = results.stream()
                .filter(r -> "LARGE_TXN_001".equals(r.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(largeHit, "应命中大额交易规则");
        assertTrue(largeHit.getMatchResult(), "大额现金交易应被命中");
    }

    /**
     * 测试大额交易规则 - 转账交易不命中
     */
    @Test
    @DisplayName("大额交易规则-转账 -> 金额1000000未超过转账阈值2000000，不应命中")
    void evaluate_largeTxnTransferRule_noMatch() {
        // 准备大额交易规则
        RuleDefinition largeTxnRule = createLargeTxnRule("LARGE_TXN_002", 6L);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(largeTxnRule));

        // 准备转账交易：100万 < 200万转账阈值
        Transaction txn = createTestTransaction(7L, new BigDecimal("1000000"));
        txn.setPaymentMethod("TRANSFER");

        // 执行
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：不应命中大额交易规则
        RuleExecutionLog largeHit = results.stream()
                .filter(r -> "LARGE_TXN_002".equals(r.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNull(largeHit, "转账100万不应命中200万转账阈值");
    }

    /**
     * 测试阈值规则边界值 - 恰好等于阈值
     */
    @Test
    @DisplayName("阈值规则边界 -> 金额恰好等于阈值10000，应命中(>=)")
    void evaluate_thresholdRuleExactBoundary() {
        // 准备规则：阈值=10000
        RuleDefinition thresholdRule = createThresholdRule("RULE_BOUNDARY", 7L, 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        // 准备交易：金额恰好=10000
        Transaction txn = createTestTransaction(8L, new BigDecimal("10000"));

        // 执行
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：边界值应命中（>= 操作符）
        RuleExecutionLog boundaryHit = results.stream()
                .filter(r -> "RULE_BOUNDARY".equals(r.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(boundaryHit, "金额恰好等于阈值应命中(>=)");
        assertTrue(boundaryHit.getMatchResult(), "边界值应匹配");
    }

    /**
     * 测试ML异常检测高分命中
     */
    @Test
    @DisplayName("ML高危异常 -> 异常分数0.9命中高危，返回ML命中结果")
    void evaluate_mlHighAnomaly_matches() {
        // 准备：ML返回高异常分数
        when(anomalyDetector.predict(any(Transaction.class))).thenReturn(0.9);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleExecutionLogMapper.insert(any())).thenReturn(1);

        Transaction txn = createTestTransaction(9L, new BigDecimal("500000"));

        // 执行
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：应有ML命中
        RuleExecutionLog mlHit = results.stream()
                .filter(r -> "ML_ISOLATION_FOREST".equals(r.getRuleCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(mlHit, "ML高危异常应命中");
        assertTrue(mlHit.getMatchResult(), "ML结果应为命中");
        assertEquals(BigDecimal.valueOf(95), mlHit.getMatchScore(), "高危异常分数应为95");
    }

    /**
     * 测试ML异常检测正常不命中
     */
    @Test
    @DisplayName("ML正常交易 -> 异常分数0.3不命中，无ML结果")
    void evaluate_mlNormalTransaction_noMatch() {
        // 准备：ML返回正常分数
        when(anomalyDetector.predict(any(Transaction.class))).thenReturn(0.3);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Transaction txn = createTestTransaction(10L, new BigDecimal("5000"));

        // 执行
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：不应有ML命中
        boolean mlHit = results.stream()
                .anyMatch(r -> "ML_ISOLATION_FOREST".equals(r.getRuleCode()));
        assertFalse(mlHit, "ML正常交易不应命中");
    }
}
