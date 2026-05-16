package com.insurance.aml.module.regulation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 法规及资料库文档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_regulation_document")
@Schema(description = "法规及资料库文档")
public class RegulationDocument extends BaseEntity {

    @Schema(description = "资料编码")
    private String docCode;

    @Schema(description = "资料标题")
    private String title;

    @Schema(description = "资料类型")
    private String docType;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

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

    @Schema(description = "查看次数")
    private Integer viewCount;
}
