package com.insurance.aml.module.monitoring.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则反馈统计VO
 * 展示单条规则的历史命中、确认、误报等核心指标及阈值调整建议
 */
@Data
public class RuleFeedbackVO {

    /** 规则ID */
    private Long ruleId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则分类 */
    private String ruleCategory;

    /** 规则当前状态 */
    private String status;

    /** 当前配置JSON（含阈值） */
    private String configJson;

    // ========== 统计指标 ==========

    /** 总命中次数（规则执行日志中matchResult=true的记录数） */
    private Long totalHits;

    /** 关联预警数（由该规则产生的预警总数） */
    private Long alertCount;

    /** 已确认可疑数（processResult=CONFIRMED_SUSPICIOUS） */
    private Long confirmedCount;

    /** 已排除数（processResult=EXCLUDED 或 status=EXCLUDED） */
    private Long excludedCount;

    /** 已升级数（processResult=ESCALATED） */
    private Long escalatedCount;

    /** 待处理数（status=NEW/ASSIGNED/PROCESSING） */
    private Long pendingCount;

    // ========== 比率指标 ==========

    /** 确认率 = confirmedCount / alertCount */
    private BigDecimal confirmationRate;

    /** 误报率 = excludedCount / alertCount */
    private BigDecimal falsePositiveRate;

    /** 升级率 = escalatedCount / alertCount */
    private BigDecimal escalationRate;

    /** 处理率 = (confirmedCount + excludedCount + escalatedCount) / alertCount */
    private BigDecimal processingRate;

    // ========== 建议 ==========

    /**
     * 阈值调整建议方向
     * TIGHTEN  - 建议收紧阈值（确认率高但命中少，可降低阈值捕获更多）
     * RELAX    - 建议放松阈值（确认率低且命中多，应提高阈值减少噪音）
     * OPTIMAL  - 当前阈值较合理
     * INSUFFICIENT_DATA - 数据不足，暂不建议
     */
    private String adjustmentDirection;

    /** 建议说明（人可读） */
    private String adjustmentReason;

    /** 具体数值建议（如 "建议将threshold从50000提高至80000"） */
    private String adjustmentDetail;

    /** 统计时间范围 */
    private String statsPeriod;

    /** 统计计算时间 */
    private LocalDateTime calculatedAt;

    /** 所有规则统计的汇总信息 */
    @Data
    public static class FeedbackSummary {
        /** 规则总数 */
        private Integer totalRules;
        /** 平均确认率 */
        private BigDecimal avgConfirmationRate;
        /** 平均误报率 */
        private BigDecimal avgFalsePositiveRate;
        /** 建议收紧的规则数 */
        private Integer tightenCount;
        /** 建议放松的规则数 */
        private Integer relaxCount;
        /** 数据不足的规则数 */
        private Integer insufficientDataCount;
        /** 所有规则的详细统计 */
        private List<RuleFeedbackVO> ruleDetails;
    }
}
