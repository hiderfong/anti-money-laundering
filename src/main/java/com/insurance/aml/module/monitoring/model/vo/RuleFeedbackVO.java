package com.insurance.aml.module.monitoring.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则反馈统计VO
 * 展示单条规则的历史命中、确认、误报等核心指标及阈值调整建议
 */
@Data
@Schema(description = "规则反馈统计视图对象")
public class RuleFeedbackVO {

    @Schema(description = "规则ID")
    private Long ruleId;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "规则分类")
    private String ruleCategory;

    @Schema(description = "规则当前状态")
    private String status;

    @Schema(description = "当前配置JSON，含阈值")
    private String configJson;

    // ========== 统计指标 ==========

    @Schema(description = "总命中次数")
    private Long totalHits;

    @Schema(description = "关联预警数")
    private Long alertCount;

    @Schema(description = "已确认可疑数")
    private Long confirmedCount;

    @Schema(description = "已排除数")
    private Long excludedCount;

    @Schema(description = "已升级数")
    private Long escalatedCount;

    @Schema(description = "待处理数")
    private Long pendingCount;

    // ========== 比率指标 ==========

    @Schema(description = "确认率")
    private BigDecimal confirmationRate;

    @Schema(description = "误报率")
    private BigDecimal falsePositiveRate;

    @Schema(description = "升级率")
    private BigDecimal escalationRate;

    @Schema(description = "处理率")
    private BigDecimal processingRate;

    // ========== 建议 ==========

    /**
     * 阈值调整建议方向
     * TIGHTEN  - 建议收紧阈值（确认率高但命中少，可降低阈值捕获更多）
     * RELAX    - 建议放松阈值（确认率低且命中多，应提高阈值减少噪音）
     * OPTIMAL  - 当前阈值较合理
     * INSUFFICIENT_DATA - 数据不足，暂不建议
     */
    @Schema(description = "阈值调整建议方向，可选值：TIGHTEN-收紧、RELAX-放松、OPTIMAL-合理、INSUFFICIENT_DATA-数据不足")
    private String adjustmentDirection;

    @Schema(description = "建议说明")
    private String adjustmentReason;

    @Schema(description = "具体数值建议")
    private String adjustmentDetail;

    @Schema(description = "统计时间范围")
    private String statsPeriod;

    @Schema(description = "统计计算时间")
    private LocalDateTime calculatedAt;

    @Schema(description = "所有规则统计的汇总信息")
    @Data
    public static class FeedbackSummary {
        @Schema(description = "规则总数")
        private Integer totalRules;
        @Schema(description = "平均确认率")
        private BigDecimal avgConfirmationRate;
        @Schema(description = "平均误报率")
        private BigDecimal avgFalsePositiveRate;
        @Schema(description = "建议收紧的规则数")
        private Integer tightenCount;
        @Schema(description = "建议放松的规则数")
        private Integer relaxCount;
        @Schema(description = "数据不足的规则数")
        private Integer insufficientDataCount;
        @Schema(description = "所有规则的详细统计")
        private List<RuleFeedbackVO> ruleDetails;
    }
}
