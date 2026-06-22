package com.insurance.aml.module.product.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 产品风险评估请求DTO
 */
@Data
@Schema(description = "产品风险评估请求")
public class ProductRiskAssessRequest {

    @Schema(description = "产品ID，优先使用路径参数")
    private Long productId;

    @Schema(description = "评估人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "评估人不能为空")
    private String assessor;

    @Schema(description = "客户群体评分（0-100）")
    @NotNull(message = "客户群体评分不能为空")
    @Min(value = 0, message = "客户群体评分不能小于0")
    @Max(value = 100, message = "客户群体评分不能大于100")
    private Integer clientGroupScore;

    @Schema(description = "缴费方式评分（0-100）")
    @NotNull(message = "缴费方式评分不能为空")
    @Min(value = 0, message = "缴费方式评分不能小于0")
    @Max(value = 100, message = "缴费方式评分不能大于100")
    private Integer paymentModeScore;

    @Schema(description = "产品结构评分（0-100）")
    @NotNull(message = "产品结构评分不能为空")
    @Min(value = 0, message = "产品结构评分不能小于0")
    @Max(value = 100, message = "产品结构评分不能大于100")
    private Integer productStructureScore;

    @Schema(description = "退保风险评分（0-100）")
    @NotNull(message = "退保风险评分不能为空")
    @Min(value = 0, message = "退保风险评分不能小于0")
    @Max(value = 100, message = "退保风险评分不能大于100")
    private Integer surrenderScore;

    @Schema(description = "受益人风险评分（0-100）")
    @NotNull(message = "受益人风险评分不能为空")
    @Min(value = 0, message = "受益人风险评分不能小于0")
    @Max(value = 100, message = "受益人风险评分不能大于100")
    private Integer beneficiaryScore;

    @Schema(description = "销售渠道评分（0-100）")
    @NotNull(message = "销售渠道评分不能为空")
    @Min(value = 0, message = "销售渠道评分不能小于0")
    @Max(value = 100, message = "销售渠道评分不能大于100")
    private Integer channelScore;
}
