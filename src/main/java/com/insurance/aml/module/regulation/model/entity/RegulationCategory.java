package com.insurance.aml.module.regulation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 法规资料分类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_regulation_category")
@Schema(description = "法规资料分类")
public class RegulationCategory extends BaseEntity {

    @Schema(description = "分类编码")
    private String categoryCode;

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
