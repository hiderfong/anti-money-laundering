package com.insurance.aml.module.product.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品分页查询请求DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "产品分页查询请求")
public class ProductQueryRequest extends PageQuery {

    @Schema(description = "产品名称（模糊查询）")
    private String productName;

    @Schema(description = "产品类型")
    private String productType;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "状态")
    private String status;
}
