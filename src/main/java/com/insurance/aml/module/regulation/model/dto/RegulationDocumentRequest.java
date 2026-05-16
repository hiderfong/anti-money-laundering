package com.insurance.aml.module.regulation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 法规资料文档维护请求。
 */
@Data
@Schema(description = "法规资料文档维护请求")
public class RegulationDocumentRequest {

    @NotBlank(message = "资料编码不能为空")
    @Schema(description = "资料编码")
    private String docCode;

    @NotBlank(message = "资料标题不能为空")
    @Schema(description = "资料标题")
    private String title;

    @NotBlank(message = "资料类型不能为空")
    @Schema(description = "资料类型：REGULATION/POLICY/TRAINING/REGULATORY_UPDATE/INDUSTRY_UPDATE")
    private String docType;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "来源类型：REGULATOR/INDUSTRY/INTERNAL")
    private String sourceType;

    @Schema(description = "发布机构或来源")
    private String sourceOrg;

    @Schema(description = "发布日期")
    private LocalDate publishDate;

    @Schema(description = "生效日期")
    private LocalDate effectiveDate;

    @Schema(description = "状态：DRAFT/PUBLISHED/ARCHIVED")
    private String status;

    @Schema(description = "是否重点资料")
    private Boolean importantFlag;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "正文内容")
    private String content;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "来源链接")
    private String referenceUrl;

    @Schema(description = "附件引用")
    private String attachmentRef;
}
