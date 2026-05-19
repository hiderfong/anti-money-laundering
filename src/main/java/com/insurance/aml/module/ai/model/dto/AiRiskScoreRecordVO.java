package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI风险评分历史记录。
 */
@Data
@Builder
@Schema(description = "AI风险评分历史记录")
public class AiRiskScoreRecordVO {

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

    @Schema(description = "模型ID")
    private Long modelId;

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

    @Schema(description = "主要贡献因子摘要")
    private String factorSummary;

    @Schema(description = "评分时间")
    private LocalDateTime scoredAt;
}
