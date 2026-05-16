package com.insurance.aml.module.product.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 产品创建请求DTO
 */
@Data
@Schema(description = "产品创建请求")
public class ProductCreateRequest {

    @Schema(description = "产品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品编码不能为空")
    private String productCode;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品名称不能为空")
    private String productName;

    @Schema(description = "产品描述")
    private String description;

    @Schema(description = "产品类型（LIFE/PROPERTY/HEALTH/ACCIDENT/ANNUITY）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品类型不能为空")
    private String productType;

    @Schema(description = "产品子类型")
    private String productSubType;

    @Schema(description = "缴费方式（LUMP_SUM/PERIODIC/FLEXIBLE）")
    private String paymentMode;

    @Schema(description = "是否具有现金价值")
    private Boolean hasCashValue;

    @Schema(description = "是否具有投资功能")
    private Boolean hasInvestmentFeature;

    @Schema(description = "退保灵活性（LOW/MEDIUM/HIGH）")
    private String surrenderFlexibility;

    @Schema(description = "受益人是否可变更")
    private Boolean beneficiaryChangeable;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期")
    private LocalDate expiryDate;
}
