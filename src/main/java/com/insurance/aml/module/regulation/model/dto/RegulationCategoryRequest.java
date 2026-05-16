package com.insurance.aml.module.regulation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 法规资料分类维护请求。
 */
@Data
@Schema(description = "法规资料分类维护请求")
public class RegulationCategoryRequest {

    @NotBlank(message = "分类编码不能为空")
    @Schema(description = "分类编码")
    private String categoryCode;

    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "分类类型：REGULATION/POLICY/TRAINING/UPDATE/GENERAL")
    private String categoryType;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "状态：ENABLED/DISABLED")
    private String status;

    @Schema(description = "分类说明")
    private String description;
}
