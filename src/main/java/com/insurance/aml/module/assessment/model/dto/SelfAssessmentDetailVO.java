package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 风险自评估详情VO（含评分明细）
 */
@Data
@Schema(description = "风险自评估详情VO")
public class SelfAssessmentDetailVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "评估年度")
    private Integer assessmentYear;

    @Schema(description = "评估周期")
    private String assessmentPeriod;

    @Schema(description = "评估状态")
    private String assessmentStatus;

    @Schema(description = "评估人ID")
    private Long assessorId;

    @Schema(description = "固有风险评分")
    private Integer inherentRiskScore;

    @Schema(description = "控制有效性评分")
    private Integer controlEffectivenessScore;

    @Schema(description = "综合评分")
    private Integer overallScore;

    @Schema(description = "综合风险等级")
    private String overallRiskLevel;

    @Schema(description = "评估结论")
    private String conclusion;

    @Schema(description = "审批人")
    private String approvedBy;

    @Schema(description = "审批时间")
    private LocalDateTime approvedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "评分明细列表（含指标详情）")
    private List<AssessmentScoreVO> scores;
}
