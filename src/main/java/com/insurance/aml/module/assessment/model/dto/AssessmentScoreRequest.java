package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评估评分请求DTO
 */
@Data
@Schema(description = "评估评分请求")
public class AssessmentScoreRequest {

    @Schema(description = "评估ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评估ID不能为空")
    private Long assessmentId;

    @Schema(description = "指标ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "指标ID不能为空")
    private Long indicatorId;

    @Schema(description = "原始值")
    private BigDecimal rawValue;

    @Schema(description = "评分")
    @NotNull(message = "评分不能为空")
    @Min(value = 0, message = "评分不能小于0")
    @Max(value = 100, message = "评分不能大于100")
    private Integer score;

    @Schema(description = "评分依据/证据")
    private String evidence;

    @Schema(description = "数据来源")
    private String dataSource;

    @Schema(description = "备注")
    private String remark;
}
