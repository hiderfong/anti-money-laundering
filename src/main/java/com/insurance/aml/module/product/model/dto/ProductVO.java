package com.insurance.aml.module.product.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品视图对象VO
 */
@Data
@Schema(description = "产品详情")
public class ProductVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "产品类型")
    private String productType;

    @Schema(description = "产品子类型")
    private String productSubType;

    @Schema(description = "缴费方式")
    private String paymentMode;

    @Schema(description = "是否具有现金价值")
    private Boolean hasCashValue;

    @Schema(description = "是否具有投资功能")
    private Boolean hasInvestmentFeature;

    @Schema(description = "退保灵活性")
    private String surrenderFlexibility;

    @Schema(description = "受益人是否可变更")
    private Boolean beneficiaryChangeable;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "风险评分")
    private Integer riskScore;

    @Schema(description = "风险因素说明")
    private String riskFactors;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "失效日期")
    private LocalDate expiryDate;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新人")
    private String updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
