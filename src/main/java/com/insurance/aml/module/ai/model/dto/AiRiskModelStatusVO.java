package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI风险评分底座状态。
 */
@Data
@Schema(description = "AI风险评分底座状态")
public class AiRiskModelStatusVO {

    @Schema(description = "模型编码")
    private String modelCode = "AI_AML_RISK_BASELINE_V1";

    @Schema(description = "模型ID")
    private Long modelId;

    @Schema(description = "模型名称")
    private String modelName = "AI可解释风险评分基线模型";

    @Schema(description = "模型版本")
    private String modelVersion = "1.0.0";

    @Schema(description = "模型类型")
    private String modelType = "EXPLAINABLE_BASELINE";

    @Schema(description = "模型状态")
    private String status = "SERVING";

    @Schema(description = "模型生命周期状态")
    private String lifecycleStatus = "MONITORING";

    @Schema(description = "模型监控状态")
    private String monitorStatus = "NORMAL";

    @Schema(description = "累计评分记录数")
    private long scoringRecordCount;

    @Schema(description = "最近评分时间")
    private LocalDateTime lastScoredAt;

    @Schema(description = "是否需要外部模型服务")
    private boolean externalModelRequired = false;

    @Schema(description = "评分主体范围")
    private List<String> supportedSubjects = new ArrayList<>(List.of("CUSTOMER", "TRANSACTION", "ALERT"));

    @Schema(description = "模型说明")
    private String description = "基于客户身份、交易行为、名单筛查、预警案件、STR和关系复杂度的可解释AI风险评分基线。";

    @Schema(description = "状态生成时间")
    private LocalDateTime generatedAt = LocalDateTime.now();
}
