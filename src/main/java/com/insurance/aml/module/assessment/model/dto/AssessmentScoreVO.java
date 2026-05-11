package com.insurance.aml.module.assessment.model.dto;

import com.insurance.aml.module.assessment.model.entity.AssessmentIndicator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估评分明细VO（含指标详情）
 */
@Data
@Schema(description = "评估评分明细VO")
public class AssessmentScoreVO {

    @Schema(description = "评分ID")
    private Long id;

    @Schema(description = "评估ID")
    private Long assessmentId;

    @Schema(description = "指标ID")
    private Long indicatorId;

    @Schema(description = "指标编码")
    private String indicatorCode;

    @Schema(description = "指标名称")
    private String indicatorName;

    @Schema(description = "指标分类")
    private String category;

    @Schema(description = "评估维度")
    private String dimension;

    @Schema(description = "权重")
    private BigDecimal weight;

    @Schema(description = "原始值")
    private BigDecimal rawValue;

    @Schema(description = "评分")
    private Integer score;

    @Schema(description = "评分依据")
    private String evidence;

    @Schema(description = "数据来源")
    private String dataSource;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "评分人")
    private String scoredBy;

    @Schema(description = "评分时间")
    private LocalDateTime scoredTime;
}
