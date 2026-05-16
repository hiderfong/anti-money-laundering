package com.insurance.aml.module.regulation.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 法规资料全文检索与筛选请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "法规资料全文检索与筛选请求")
public class RegulationDocumentQueryRequest extends PageQuery {

    @Schema(description = "全文关键字")
    private String keyword;

    @Schema(description = "资料类型")
    private String docType;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "是否重点资料")
    private Boolean importantFlag;
}
