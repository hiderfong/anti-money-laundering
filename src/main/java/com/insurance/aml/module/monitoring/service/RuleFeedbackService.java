package com.insurance.aml.module.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.aml.common.enums.AlertProcessResult;
import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.model.vo.RuleFeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则自学习反馈服务
 *
 * 核心闭环逻辑:
 *   规则执行 → 产生预警 → 人工处理(CONFIRMED/EXCLUDED/ESCALATED) → 统计反馈 → 阈值优化建议
 *
 * 统计维度:
 *   - 确认率: 该规则关联预警中 processResult=CONFIRMED_SUSPICIOUS 的比例
 *   - 误报率: 该规则关联预警中 processResult=EXCLUDED 或 status=EXCLUDED 的比例
 *   - 命中量: t_rule_execution_log 中该规则 matchResult=true 的记录数
 *
 * 阈值建议策略:
 *   - 确认率 < 10% 且命中量 > 100  → RELAX (阈值过低，大量误报，建议提高阈值)
 *   - 确认率 > 50% 且命中量 < 10   → TIGHTEN (阈值过高，漏检风险，建议降低阈值)
 *   - 10% ≤ 确认率 ≤ 50%           → OPTIMAL (阈值合理)
 *   - 数据不足                      → INSUFFICIENT_DATA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleFeedbackService {

    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleExecutionLogMapper ruleExecutionLogMapper;
    private final AlertMapper alertMapper;
    private final ObjectMapper objectMapper;

    /** 确认率低阈值 */
    private static final BigDecimal LOW_CONFIRMATION_THRESHOLD = new BigDecimal("0.10");
    /** 确认率高阈值 */
    private static final BigDecimal HIGH_CONFIRMATION_THRESHOLD = new BigDecimal("0.50");
    /** 最小命中量（低于此值不建议调整） */
    private static final long MIN_HITS_FOR_ADJUSTMENT = 10;
    /** 高命中量阈值 */
    private static final long HIGH_HITS_THRESHOLD = 100;

    /**
     * 统计所有规则的反馈数据，返回汇总结果
     *
     * @return FeedbackSummary 包含每条规则的详细统计和全局汇总
     */
    public RuleFeedbackVO.FeedbackSummary calculateRuleStats() {
        log.info("[规则反馈] 开始计算所有规则反馈统计");

        List<RuleDefinition> allRules = ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinition>().eq(RuleDefinition::getStatus, "ENABLED")
        );

        List<RuleFeedbackVO> details = new ArrayList<>();
        BigDecimal totalConfirmationRate = BigDecimal.ZERO;
        BigDecimal totalFalsePositiveRate = BigDecimal.ZERO;
        int tightenCount = 0;
        int relaxCount = 0;
        int insufficientCount = 0;

        for (RuleDefinition rule : allRules) {
            RuleFeedbackVO vo = calculateSingleRuleStats(rule);
            details.add(vo);

            if (vo.getConfirmationRate() != null) {
                totalConfirmationRate = totalConfirmationRate.add(vo.getConfirmationRate());
                totalFalsePositiveRate = totalFalsePositiveRate.add(vo.getFalsePositiveRate());
            }

            switch (vo.getAdjustmentDirection()) {
                case "TIGHTEN":
                    tightenCount++;
                    break;
                case "RELAX":
                    relaxCount++;
                    break;
                case "INSUFFICIENT_DATA":
                    insufficientCount++;
                    break;
                default:
                    break;
            }
        }

        int ruleCount = allRules.size();
        RuleFeedbackVO.FeedbackSummary summary = new RuleFeedbackVO.FeedbackSummary();
        summary.setTotalRules(ruleCount);
        summary.setRuleDetails(details);
        summary.setTightenCount(tightenCount);
        summary.setRelaxCount(relaxCount);
        summary.setInsufficientDataCount(insufficientCount);

        if (ruleCount > 0) {
            summary.setAvgConfirmationRate(totalConfirmationRate.divide(BigDecimal.valueOf(ruleCount), 4, RoundingMode.HALF_UP));
            summary.setAvgFalsePositiveRate(totalFalsePositiveRate.divide(BigDecimal.valueOf(ruleCount), 4, RoundingMode.HALF_UP));
        } else {
            summary.setAvgConfirmationRate(BigDecimal.ZERO);
            summary.setAvgFalsePositiveRate(BigDecimal.ZERO);
        }

        log.info("[规则反馈] 统计完成: 规则总数={}, 建议收紧={}, 建议放松={}, 数据不足={}",
                ruleCount, tightenCount, relaxCount, insufficientCount);

        return summary;
    }

    /**
     * 统计单条规则的反馈数据
     *
     * @param rule 规则定义
     * @return 该规则的详细统计VO
     */
    public RuleFeedbackVO calculateSingleRuleStats(RuleDefinition rule) {
        RuleFeedbackVO vo = new RuleFeedbackVO();
        vo.setRuleId(rule.getId());
        vo.setRuleCode(rule.getRuleCode());
        vo.setRuleName(rule.getRuleName());
        vo.setRuleCategory(rule.getRuleCategory());
        vo.setStatus(rule.getStatus());
        vo.setConfigJson(rule.getConfigJson());
        vo.setCalculatedAt(LocalDateTime.now());
        vo.setStatsPeriod("ALL_TIME");

        // 1. 命中次数: t_rule_execution_log 中 matchResult=true
        Long totalHits = ruleExecutionLogMapper.selectCount(
                new LambdaQueryWrapper<RuleExecutionLog>()
                        .eq(RuleExecutionLog::getRuleCode, rule.getRuleCode())
                        .eq(RuleExecutionLog::getMatchResult, true)
        );
        vo.setTotalHits(totalHits);

        // 2. 关联预警数: t_alert 中 source_rule_codes LIKE '%ruleCode%'
        // 同时统计各处理结果
        Long alertCount = countAlertsByRule(rule.getRuleCode(), null);
        Long confirmedCount = countAlertsByRuleAndResult(rule.getRuleCode(), AlertProcessResult.CONFIRMED_SUSPICIOUS.getCode());
        Long excludedCount = countAlertsByExcluded(rule.getRuleCode());
        Long escalatedCount = countAlertsByRuleAndResult(rule.getRuleCode(), AlertProcessResult.ESCALATED.getCode());
        Long pendingCount = countAlertsByStatus(rule.getRuleCode());

        vo.setAlertCount(alertCount);
        vo.setConfirmedCount(confirmedCount);
        vo.setExcludedCount(excludedCount);
        vo.setEscalatedCount(escalatedCount);
        vo.setPendingCount(pendingCount);

        // 3. 计算比率
        if (alertCount > 0) {
            vo.setConfirmationRate(BigDecimal.valueOf(confirmedCount)
                    .divide(BigDecimal.valueOf(alertCount), 4, RoundingMode.HALF_UP));
            vo.setFalsePositiveRate(BigDecimal.valueOf(excludedCount)
                    .divide(BigDecimal.valueOf(alertCount), 4, RoundingMode.HALF_UP));
            vo.setEscalationRate(BigDecimal.valueOf(escalatedCount)
                    .divide(BigDecimal.valueOf(alertCount), 4, RoundingMode.HALF_UP));

            long processed = confirmedCount + excludedCount + escalatedCount;
            vo.setProcessingRate(BigDecimal.valueOf(processed)
                    .divide(BigDecimal.valueOf(alertCount), 4, RoundingMode.HALF_UP));
        } else {
            vo.setConfirmationRate(BigDecimal.ZERO);
            vo.setFalsePositiveRate(BigDecimal.ZERO);
            vo.setEscalationRate(BigDecimal.ZERO);
            vo.setProcessingRate(BigDecimal.ZERO);
        }

        // 4. 阈值调整建议
        suggestThresholdAdjustment(vo);

        return vo;
    }

    /**
     * 基于统计数据生成阈值调整建议
     *
     * 决策矩阵:
     * ┌──────────────────┬──────────────┬──────────────────────────────────┐
     * │ 确认率            │ 命中量        │ 建议                              │
     * ├──────────────────┼──────────────┼──────────────────────────────────┤
     * │ < 10%            │ > 100        │ RELAX - 大量误报，提高阈值           │
     * │ < 10%            │ ≤ 100        │ RELAX - 误报为主，建议提高阈值       │
     * │ > 50%            │ < 10         │ TIGHTEN - 漏检风险，降低阈值         │
     * │ > 50%            │ ≥ 10         │ OPTIMAL - 确认率高，阈值合理         │
     * │ 10% ~ 50%        │ any          │ OPTIMAL - 阈值在合理区间             │
     * │ any              │ < 10且无预警  │ INSUFFICIENT_DATA                  │
     * └──────────────────┴──────────────┴──────────────────────────────────┘
     */
    private void suggestThresholdAdjustment(RuleFeedbackVO vo) {
        BigDecimal confirmationRate = vo.getConfirmationRate();
        Long totalHits = vo.getTotalHits();
        Long alertCount = vo.getAlertCount();

        // 数据不足
        if (totalHits < MIN_HITS_FOR_ADJUSTMENT && alertCount < MIN_HITS_FOR_ADJUSTMENT) {
            vo.setAdjustmentDirection("INSUFFICIENT_DATA");
            vo.setAdjustmentReason(String.format(
                    "数据不足: 命中%d次, 预警%d条。需积累更多数据后再评估。",
                    totalHits, alertCount));
            vo.setAdjustmentDetail("暂无具体建议");
            return;
        }

        // 确认率低 → 建议放松（提高阈值）
        if (confirmationRate.compareTo(LOW_CONFIRMATION_THRESHOLD) < 0) {
            vo.setAdjustmentDirection("RELAX");
            if (totalHits > HIGH_HITS_THRESHOLD) {
                vo.setAdjustmentReason(String.format(
                        "确认率极低(%.1f%%)且命中量大(%d次), 产生大量误报, 建议提高阈值减少噪音。",
                        confirmationRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                        totalHits));
            } else {
                vo.setAdjustmentReason(String.format(
                        "确认率偏低(%.1f%%), 建议适当提高阈值。",
                        confirmationRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)));
            }
            vo.setAdjustmentDetail(generateAdjustmentDetail(vo, "RELAX"));
            return;
        }

        // 确认率高且命中量低 → 建议收紧（降低阈值）
        if (confirmationRate.compareTo(HIGH_CONFIRMATION_THRESHOLD) > 0 && totalHits < 10) {
            vo.setAdjustmentDirection("TIGHTEN");
            vo.setAdjustmentReason(String.format(
                    "确认率高(%.1f%%)但命中量低(%d次), 存在漏检风险, 建议降低阈值捕获更多可疑交易。",
                    confirmationRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                    totalHits));
            vo.setAdjustmentDetail(generateAdjustmentDetail(vo, "TIGHTEN"));
            return;
        }

        // 合理区间
        vo.setAdjustmentDirection("OPTIMAL");
        vo.setAdjustmentReason(String.format(
                "确认率(%.1f%%)和命中量(%d次)在合理区间, 暂无需调整。",
                confirmationRate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP),
                totalHits));
        vo.setAdjustmentDetail("当前阈值配置合理");
    }

    /**
     * 生成具体的阈值调整数值建议
     * 从configJson中提取当前阈值，按方向建议调整幅度
     */
    private String generateAdjustmentDetail(RuleFeedbackVO vo, String direction) {
        try {
            if (vo.getConfigJson() == null || vo.getConfigJson().isBlank()) {
                return "规则未配置JSON参数，无法给出具体数值建议，请人工评估。";
            }

            JsonNode config = objectMapper.readTree(vo.getConfigJson());
            List<String> suggestions = new ArrayList<>();

            // 扫描config中的阈值字段，生成建议
            String[] thresholdFields = {"threshold", "cash_threshold", "transfer_threshold",
                    "cross_border_threshold", "max_count", "max_amount"};

            for (String field : thresholdFields) {
                if (config.has(field)) {
                    BigDecimal current = new BigDecimal(config.get(field).asText());
                    BigDecimal suggested;

                    if ("RELAX".equals(direction)) {
                        // 提高阈值50%
                        suggested = current.multiply(new BigDecimal("1.5")).setScale(0, RoundingMode.HALF_UP);
                        suggestions.add(String.format("建议将%s从%s提高至%s(+50%%)", field, current.toPlainString(), suggested.toPlainString()));
                    } else {
                        // 降低阈值30%
                        suggested = current.multiply(new BigDecimal("0.7")).setScale(0, RoundingMode.HALF_UP);
                        suggestions.add(String.format("建议将%s从%s降低至%s(-30%%)", field, current.toPlainString(), suggested.toPlainString()));
                    }
                }
            }

            // time_window_seconds 反向调整
            if (config.has("time_window_seconds") && "RELAX".equals(direction)) {
                int window = config.get("time_window_seconds").asInt();
                int suggestedWindow = (int) (window * 0.7);
                suggestions.add(String.format("建议将time_window_seconds从%d缩短至%d(-30%%)", window, suggestedWindow));
            }

            if (suggestions.isEmpty()) {
                return "规则配置中未找到可识别的阈值字段，请人工评估config_json: " + vo.getConfigJson();
            }
            return String.join("; ", suggestions);

        } catch (Exception e) {
            log.warn("解析规则配置JSON失败: ruleCode={}, error={}", vo.getRuleCode(), e.getMessage());
            return "规则配置JSON解析失败，请人工评估。";
        }
    }

    // ========== Alert 统计查询方法 ==========

    /**
     * 统计某规则关联的预警数（按processResult筛选）
     */
    private Long countAlertsByRuleAndResult(String ruleCode, String processResult) {
        return alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .like(Alert::getSourceRuleCodes, ruleCode)
                        .eq(Alert::getProcessResult, processResult)
        );
    }

    /**
     * 统计已排除的预警数（processResult=EXCLUDED 或 status=EXCLUDED）
     */
    private Long countAlertsByExcluded(String ruleCode) {
        return alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .like(Alert::getSourceRuleCodes, ruleCode)
                        .and(w -> w.eq(Alert::getProcessResult, AlertProcessResult.EXCLUDED.getCode())
                                .or()
                                .eq(Alert::getStatus, AlertStatus.EXCLUDED.getCode()))
        );
    }

    /**
     * 统计某规则关联的预警总数（可选状态筛选）
     */
    private Long countAlertsByRule(String ruleCode, String status) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<Alert>()
                .like(Alert::getSourceRuleCodes, ruleCode);
        if (status != null) {
            wrapper.eq(Alert::getStatus, status);
        }
        return alertMapper.selectCount(wrapper);
    }

    /**
     * 统计待处理预警数（status IN NEW/ASSIGNED/PROCESSING）
     */
    private Long countAlertsByStatus(String ruleCode) {
        return alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .like(Alert::getSourceRuleCodes, ruleCode)
                        .in(Alert::getStatus, AlertStatus.NEW.getCode(), AlertStatus.ASSIGNED.getCode(), AlertStatus.PROCESSING.getCode())
        );
    }

    /**
     * 查询单条规则的反馈详情
     */
    public RuleFeedbackVO getRuleFeedback(Long ruleId) {
        RuleDefinition rule = ruleDefinitionMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在: id=" + ruleId);
        }
        return calculateSingleRuleStats(rule);
    }

    /**
     * 查询单条规则的反馈详情（按ruleCode）
     */
    public RuleFeedbackVO getRuleFeedbackByCode(String ruleCode) {
        RuleDefinition rule = ruleDefinitionMapper.selectOne(
                new LambdaQueryWrapper<RuleDefinition>()
                        .eq(RuleDefinition::getRuleCode, ruleCode)
        );
        if (rule == null) {
            throw new RuntimeException("规则不存在: ruleCode=" + ruleCode);
        }
        return calculateSingleRuleStats(rule);
    }

    /**
     * 获取需要关注的规则列表（RELAX或TIGHTEN建议）
     */
    public List<RuleFeedbackVO> getRulesNeedingAttention() {
        RuleFeedbackVO.FeedbackSummary summary = calculateRuleStats();
        return summary.getRuleDetails().stream()
                .filter(v -> "RELAX".equals(v.getAdjustmentDirection())
                        || "TIGHTEN".equals(v.getAdjustmentDirection()))
                .toList();
    }
}
