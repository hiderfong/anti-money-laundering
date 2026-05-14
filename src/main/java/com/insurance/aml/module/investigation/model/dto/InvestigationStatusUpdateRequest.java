package com.insurance.aml.module.investigation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 调查协查状态更新参数。
 */
@Data
@Schema(description = "调查协查状态更新参数")
public class InvestigationStatusUpdateRequest {

    @Schema(description = "状态：RECEIVED/PROCESSING/WAITING_APPROVAL/RESPONDED/CLOSED/RETURNED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态不能为空")
    private String status;

    @Schema(description = "回复摘要")
    private String responseSummary;
}
