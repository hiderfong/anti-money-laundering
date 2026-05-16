package com.insurance.aml.module.regulation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 法规资料库概览。
 */
@Data
@Builder
@Schema(description = "法规资料库概览")
public class RegulationOverviewVO {

    @Schema(description = "资料总数")
    private long totalDocuments;

    @Schema(description = "法律法规数")
    private long regulationDocuments;

    @Schema(description = "制度文件数")
    private long policyDocuments;

    @Schema(description = "培训素材数")
    private long trainingDocuments;

    @Schema(description = "监管动态数")
    private long regulatoryUpdates;

    @Schema(description = "行业动态数")
    private long industryUpdates;

    @Schema(description = "已发布资料数")
    private long publishedDocuments;

    @Schema(description = "重点资料数")
    private long importantDocuments;

    @Schema(description = "分类数")
    private long categories;
}
