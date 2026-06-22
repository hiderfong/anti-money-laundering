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
     * 报告ID。控制器优先使用路径参数填充，保留该字段兼容旧客户端。
     */
    @Schema(description = "报告ID，由路径参数填充，兼容旧客户端请求体传值")
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
