package com.insurance.aml.module.monitoring.task;

import com.insurance.aml.module.monitoring.model.vo.RuleFeedbackVO;
import com.insurance.aml.module.monitoring.service.RuleFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则效果定期统计任务
 *
 * 每周日凌晨2点自动执行规则反馈统计，记录日志供运维查看
 * 形成"规则执行 → 人工反馈 → 阈值优化"的闭环
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleFeedbackScheduledTask {

    private final RuleFeedbackService ruleFeedbackService;

    /**
     * 每周日凌晨2:00执行规则效果统计
     * 输出每条规则的确认率、误报率、阈值建议
     */
    @Scheduled(cron = "0 0 2 ? * SUN")
    public void weeklyRuleFeedbackReport() {
        log.info("====== [定时任务] 规则效果周报 开始 ======");

        try {
            RuleFeedbackVO.FeedbackSummary summary = ruleFeedbackService.calculateRuleStats();

            log.info("[规则效果周报] 规则总数: {}", summary.getTotalRules());
            log.info("[规则效果周报] 平均确认率: {}%", summary.getAvgConfirmationRate().multiply(new java.math.BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP));
            log.info("[规则效果周报] 平均误报率: {}%", summary.getAvgFalsePositiveRate().multiply(new java.math.BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP));
            log.info("[规则效果周报] 建议收紧: {}条, 建议放松: {}条, 数据不足: {}条",
                    summary.getTightenCount(), summary.getRelaxCount(), summary.getInsufficientDataCount());

            // 输出需要关注的规则详情
            for (RuleFeedbackVO vo : summary.getRuleDetails()) {
                if (!"OPTIMAL".equals(vo.getAdjustmentDirection()) && !"INSUFFICIENT_DATA".equals(vo.getAdjustmentDirection())) {
                    log.warn("[规则效果周报] 需关注规则: ruleCode={}, ruleName={}, 确认率={}%, 误报率={}%, 命中={}, 建议={} - {}",
                            vo.getRuleCode(), vo.getRuleName(),
                            vo.getConfirmationRate().multiply(new java.math.BigDecimal("100")).setScale(1, java.math.RoundingMode.HALF_UP),
                            vo.getFalsePositiveRate().multiply(new java.math.BigDecimal("100")).setScale(1, java.math.RoundingMode.HALF_UP),
                            vo.getTotalHits(),
                            vo.getAdjustmentDirection(),
                            vo.getAdjustmentReason());
                }
            }

            // 检查是否有需要立即关注的规则
            List<RuleFeedbackVO> attention = ruleFeedbackService.getRulesNeedingAttention();
            if (!attention.isEmpty()) {
                log.warn("[规则效果周报] 共{}条规则需要人工评估阈值调整!", attention.size());
            }

        } catch (Exception e) {
            log.error("[规则效果周报] 统计执行异常", e);
        }

        log.info("====== [定时任务] 规则效果周报 完成 ======");
    }
}
