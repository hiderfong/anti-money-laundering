package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI评分待复核池列表项。
 */
@Data
@Builder
@Schema(description = "AI评分待复核池列表项")
public class AiRiskReviewPoolItemVO {

    @Schema(description = "评分记录ID")
    private Long id;

    @Schema(description = "评分流水号")
    private String scoreNo;

    @Schema(description = "评分主体类型")
    private String subjectType;

    @Schema(description = "评分主体ID")
    private Long subjectId;

    @Schema(description = "评分主体名称")
    private String subjectName;

    @Schema(description = "模型编码")
    private String modelCode;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型版本")
    private String modelVersion;

    @Schema(description = "AI风险分")
    private Integer score;

    @Schema(description = "AI风险等级")
    private String riskLevel;

    @Schema(description = "置信度")
    private Integer confidence;

    @Schema(description = "自动弱标签")
    private String autoLabel;

    @Schema(description = "自动弱标签说明")
    private String autoLabelText;

    @Schema(description = "待复核状态")
    private String reviewStatus;

    @Schema(description = "复核优先级")
    private String priorityLevel;

    @Schema(description = "处置链路印证说明")
    private String verificationBasis;

    @Schema(description = "主要贡献因子摘要")
    private String factorSummary;

    @Schema(description = "评分特征快照JSON")
    private String featureSnapshotJson;

    @Schema(description = "贡献因子快照JSON")
    private String factorSnapshotJson;

    @Schema(description = "证据快照JSON")
    private String evidenceSnapshotJson;

    @Schema(description = "建议快照JSON")
    private String recommendationJson;

    @Schema(description = "监督模型影子概率")
    private java.math.BigDecimal modelProbability;

    @Schema(description = "监督模型预测标签")
    private String modelLabelPredicted;

    @Schema(description = "评分时间")
    private LocalDateTime scoredAt;

    @Schema(description = "人工复核标签")
    private String manualReviewLabel;

    @Schema(description = "人工复核标签说明")
    private String manualReviewLabelText;

    @Schema(description = "人工复核备注")
    private String manualReviewComment;

    @Schema(description = "复核人")
    private String reviewedBy;

    @Schema(description = "复核时间")
    private LocalDateTime reviewedAt;

    @Schema(description = "已生成的跟进整改任务ID")
    private Long followUpTaskId;

    @Schema(description = "跟进任务生成时间")
    private LocalDateTime followUpCreatedAt;

    @Schema(description = "跟进任务创建人")
    private String followUpCreatedBy;
}
