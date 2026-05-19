package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI辅助反洗钱风险评分结果。
 */
@Data
@Schema(description = "AI辅助反洗钱风险评分结果")
public class AiRiskScoreVO {

    @Schema(description = "评分主体类型：CUSTOMER/TRANSACTION/ALERT")
    private String subjectType;

    @Schema(description = "评分主体ID")
    private Long subjectId;

    @Schema(description = "评分主体名称")
    private String subjectName;

    @Schema(description = "AI综合风险分，0-100")
    private int score;

    @Schema(description = "AI综合风险等级")
    private String riskLevel;

    @Schema(description = "置信度，0-100")
    private int confidence;

    @Schema(description = "评分记录ID")
    private Long recordId;

    @Schema(description = "评分流水号")
    private String scoreNo;

    @Schema(description = "模型ID")
    private Long modelId;

    @Schema(description = "模型编码")
    private String modelCode = "AI_AML_RISK_BASELINE_V1";

    @Schema(description = "模型名称")
    private String modelName = "AI可解释风险评分基线模型";

    @Schema(description = "模型版本")
    private String modelVersion = "1.0.0";

    @Schema(description = "评分时间")
    private LocalDateTime scoredAt = LocalDateTime.now();

    @Schema(description = "特征摘要")
    private AiRiskFeatureSummaryVO featureSummary = new AiRiskFeatureSummaryVO();

    @Schema(description = "贡献因子")
    private List<AiRiskFactorVO> factors = new ArrayList<>();

    @Schema(description = "证据摘要")
    private List<String> evidence = new ArrayList<>();

    @Schema(description = "建议动作")
    private List<String> recommendations = new ArrayList<>();
}
