package com.insurance.aml.module.ai.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI评分待复核池查询参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI评分待复核池查询参数")
public class AiRiskReviewPoolQueryRequest extends PageQuery {

    @Schema(description = "评分主体类型：CUSTOMER/TRANSACTION/ALERT")
    private String subjectType;

    @Schema(description = "风险等级：LOW/MEDIUM/HIGH/CRITICAL")
    private String riskLevel;

    @Schema(description = "自动弱标签：LIKELY_TRUE_POSITIVE/LIKELY_FALSE_POSITIVE/UNCONFIRMED")
    private String autoLabel;

    @Schema(description = "最小AI风险分")
    private Integer minScore;

    @Schema(description = "模型编码")
    private String modelCode;

    @Schema(description = "仅展示待人工复核样本")
    private Boolean pendingOnly;
}
