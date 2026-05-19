package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI评分监控与待复核池概览。
 */
@Data
@Builder
@Schema(description = "AI评分监控与待复核池概览")
public class AiRiskReviewPoolOverviewVO {

    @Schema(description = "评分记录总数")
    private long totalScores;

    @Schema(description = "待人工复核数量")
    private long pendingReviewCount;

    @Schema(description = "系统弱标记为疑似有效风险数量")
    private long likelyTruePositiveCount;

    @Schema(description = "系统弱标记为疑似误报数量")
    private long likelyFalsePositiveCount;

    @Schema(description = "尚未被处置链路确认数量")
    private long unconfirmedCount;

    @Schema(description = "高风险及以上数量")
    private long highOrCriticalCount;

    @Schema(description = "有规则/处置链路印证数量")
    private long corroboratedCount;

    @Schema(description = "AI高分但无规则印证数量")
    private long highScoreNoRuleHitCount;

    @Schema(description = "AI低分但后续出现案件/STR数量")
    private long lowScoreWithDispositionCount;

    @Schema(description = "最近评分时间")
    private LocalDateTime latestScoredAt;
}
