package com.insurance.aml.module.screening.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审核请求DTO
 */
@Data
@Schema(description = "筛查结果审核请求")
public class ReviewRequest {

    /**
     * 筛查结果ID
     */
    @NotNull(message = "结果ID不能为空")
    @Schema(description = "筛查结果ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long resultId;

    /**
     * 审核状态：CONFIRMED-确认命中、EXCLUDED-排除误报
     */
    @NotBlank(message = "审核状态不能为空")
    @Schema(description = "审核状态（CONFIRMED/EXCLUDED）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reviewStatus;

    /**
     * 审核原因
     */
    @Schema(description = "审核原因")
    private String reviewReason;
}
