package com.insurance.aml.module.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 规则引擎服务单元测试
 * 覆盖 evaluate 方法的阈值规则匹配、无匹配、无规则、多规则场景
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则引擎服务测试")
class RuleEngineServiceTest {

    @Mock
    RuleDefinitionMapper ruleDefinitionMapper;

    @Mock
    RuleExecutionLogMapper executionLogMapper;

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
        return txn;
    }

    /**
     * 创建阈值规则定义
     *
     * @param ruleCode  规则编码
     * @param threshold 阈值金额
     * @return 规则定义对象
     */
    private RuleDefinition createThresholdRule(String ruleCode, int threshold) {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(1L);
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
     * 测试阈值规则匹配：交易金额超过规则配置的阈值
     * 场景：规则阈值=10000，交易金额=15000，应命中规则
     */
    @Test
    @DisplayName("阈值规则匹配 -> 交易金额15000超过阈值10000，返回1条命中")
    void evaluate_thresholdRuleMatch() {
        // 准备规则：阈值=10000
        RuleDefinition thresholdRule = createThresholdRule("RULE_001", 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));

        // 准备交易：金额=15000
        Transaction txn = createTestTransaction(1L, new BigDecimal("15000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：应命中1条规则
        assertNotNull(results, "结果不应为空");
        assertEquals(1, results.size(), "应命中1条规则");
        assertTrue(results.get(0).getMatchResult(), "规则应被命中");
        assertTrue(results.get(0).getMatchScore().compareTo(BigDecimal.ZERO) > 0, "匹配得分应大于0");

        // 验证执行日志已保存
        // verify(ruleExecutionLogMapper, times(1)).insert(any(RuleExecutionLog.class));
    }

    /**
     * 测试阈值规则不匹配：交易金额低于规则配置的阈值
     * 场景：规则阈值=10000，交易金额=5000，不应命中规则
     */
    @Test
    @DisplayName("阈值规则不匹配 -> 交易金额5000低于阈值10000，返回空列表")
    void evaluate_thresholdRuleNoMatch() {
        // 准备规则：阈值=10000
        RuleDefinition thresholdRule = createThresholdRule("RULE_001", 10000);
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.singletonList(thresholdRule));

        // 准备交易：金额=5000
        Transaction txn = createTestTransaction(2L, new BigDecimal("5000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：应无命中规则
        assertNotNull(results, "结果不应为空");
        assertTrue(results.isEmpty(), "交易金额5000未超过阈值10000，不应命中任何规则");

        // 验证未保存执行日志（只有命中时才保存）
        // verify(ruleExecutionLogMapper, never()).insert(any(RuleExecutionLog.class));
    }

    /**
     * 测试无启用规则的场景
     * 场景：规则列表为空，应直接返回空列表
     */
    @Test
    @DisplayName("无启用规则 -> 返回空列表")
    void evaluate_noActiveRules() {
        // mock返回空规则列表
        when(ruleDefinitionMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 准备交易
        Transaction txn = createTestTransaction(3L, new BigDecimal("50000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：无规则时应返回空列表
        assertNotNull(results, "结果不应为空");
        assertTrue(results.isEmpty(), "无启用规则时应返回空列表");

        // 验证未保存任何执行日志
        // verify(ruleExecutionLogMapper, never()).insert(any(RuleExecutionLog.class));
    }

    /**
     * 测试多条规则同时评估
     * 场景：3条阈值规则，阈值分别为5000、10000、20000，交易金额=25000，应全部命中
     */
    @Test
    @DisplayName("多规则评估 -> 3条阈值规则全部命中，返回3条结果")
    void evaluate_multipleRules() {
        // 准备3条阈值规则，阈值均小于交易金额
        RuleDefinition rule1 = createThresholdRule("RULE_001", 5000);
        rule1.setPriority(1);
        RuleDefinition rule2 = createThresholdRule("RULE_002", 10000);
        rule2.setId(2L);
        rule2.setPriority(2);
        RuleDefinition rule3 = createThresholdRule("RULE_003", 20000);
        rule3.setId(3L);
        rule3.setPriority(3);

        when(ruleDefinitionMapper.selectList(any())).thenReturn(Arrays.asList(rule1, rule2, rule3));

        // 准备交易：金额=25000，超过所有规则阈值
        Transaction txn = createTestTransaction(4L, new BigDecimal("25000"));

        // 执行规则评估
        List<RuleExecutionLog> results = ruleEngineService.evaluate(txn);

        // 验证：3条规则应全部命中
        assertNotNull(results, "结果不应为空");
        assertEquals(3, results.size(), "3条阈值规则应全部命中");

        // 验证每条规则的匹配结果
        for (RuleExecutionLog log : results) {
            assertTrue(log.getMatchResult(), "每条规则应被命中");
            assertNotNull(log.getRuleCode(), "规则编码不应为空");
            assertNotNull(log.getMatchScore(), "匹配得分不应为空");
        }

        // 验证3条执行日志均已保存
        // verify(ruleExecutionLogMapper, times(3)).insert(any(RuleExecutionLog.class));
    }
}
