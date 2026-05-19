package com.insurance.aml.module.casemgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 可疑交易报告审核请求
 */
@Data
@Schema(description = "可疑交易报告审核请求")
public class StrReportReviewRequest {

    /**
     * 报告ID（必填）
     */
    @NotNull(message = "报告ID不能为空")
    @Schema(description = "报告ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    /**
     * 是否批准（必填）
     */
    @NotNull(message = "审核结果不能为空")
    @Schema(description = "是否批准（true-批准，false-拒绝）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean approved;

    /**
     * 审核意见
     */
    @Schema(description = "审核意见")
    private String opinion;
}
