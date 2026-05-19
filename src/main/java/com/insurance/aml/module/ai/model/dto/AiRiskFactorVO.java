package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AI风险评分贡献因子。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI风险评分贡献因子")
public class AiRiskFactorVO {

    @Schema(description = "因子编码")
    private String factorCode;

    @Schema(description = "因子名称")
    private String factorName;

    @Schema(description = "因子分类")
    private String category;

    @Schema(description = "贡献分")
    private int contribution;

    @Schema(description = "权重")
    private BigDecimal weight;

    @Schema(description = "证据说明")
    private String evidence;

    @Schema(description = "建议处置")
    private String suggestion;
}
