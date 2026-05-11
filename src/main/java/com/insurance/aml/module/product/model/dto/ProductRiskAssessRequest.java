package com.insurance.aml.module.product.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产品风险评估请求DTO
 */
@Data
@Schema(description = "产品风险评估请求")
public class ProductRiskAssessRequest {

    @Schema(description = "产品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    @Schema(description = "评估人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "评估人不能为空")
    private String assessor;

    @Schema(description = "客户群体评分（0-100）")
    private Integer clientGroupScore;

    @Schema(description = "缴费方式评分（0-100）")
    private Integer paymentModeScore;

    @Schema(description = "产品结构评分（0-100）")
    private Integer productStructureScore;

    @Schema(description = "退保风险评分（0-100）")
    private Integer surrenderScore;

    @Schema(description = "受益人风险评分（0-100）")
    private Integer beneficiaryScore;

    @Schema(description = "销售渠道评分（0-100）")
    private Integer channelScore;
}
