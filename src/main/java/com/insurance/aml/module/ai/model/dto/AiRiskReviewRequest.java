package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI评分人工复核登记请求。
 */
@Data
@Schema(description = "AI评分人工复核登记请求")
public class AiRiskReviewRequest {

    @NotBlank(message = "复核标签不能为空")
    @Schema(description = "复核标签：TRUE_POSITIVE/FALSE_POSITIVE/NEEDS_MONITORING", example = "TRUE_POSITIVE")
    private String reviewLabel;

    @Schema(description = "复核备注")
    private String reviewComment;

    @Schema(description = "复核人；为空时取当前登录用户")
    private String reviewer;
}
