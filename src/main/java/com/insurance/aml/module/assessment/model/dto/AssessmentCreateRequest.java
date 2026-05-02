package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建自评估请求DTO
 */
@Data
@Schema(description = "创建风险自评估请求")
public class AssessmentCreateRequest {

    @Schema(description = "评估年度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评估年度不能为空")
    private Integer assessmentYear;

    @Schema(description = "评估周期（ANNUAL/QUARTERLY）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "评估周期不能为空")
    private String assessmentPeriod;

    @Schema(description = "评估人ID")
    private Long assessorId;
}
