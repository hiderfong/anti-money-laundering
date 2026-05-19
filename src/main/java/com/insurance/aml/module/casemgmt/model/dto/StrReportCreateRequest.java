package com.insurance.aml.module.casemgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建可疑交易报告请求
 */
@Data
@Schema(description = "创建可疑交易报告请求")
public class StrReportCreateRequest {

    /**
     * 关联案件ID（必填）
     */
    @NotNull(message = "案件ID不能为空")
    @Schema(description = "关联案件ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long caseId;

    /**
     * 报告类型：NORMAL-常规、URGENT-紧急（必填）
     */
    @NotBlank(message = "报告类型不能为空")
    @Schema(description = "报告类型（NORMAL/URGENT）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportType;

    /**
     * 报告内容
     */
    @Schema(description = "报告内容")
    private String reportContent;

    /**
     * 分析意见
     */
    @Schema(description = "分析意见")
    private String analysisOpinion;

    /**
     * 已采取措施
     */
    @Schema(description = "已采取措施")
    private String measuresTaken;
}
