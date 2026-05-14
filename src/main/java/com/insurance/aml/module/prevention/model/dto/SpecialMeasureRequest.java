package com.insurance.aml.module.prevention.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 特别预防措施创建请求。
 */
@Data
@Schema(description = "特别预防措施创建请求")
public class SpecialMeasureRequest {

    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "措施类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "措施类型不能为空")
    private String measureType;

    @Schema(description = "触发类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    @Schema(description = "关联筛查结果ID")
    private Long relatedResultId;

    @Schema(description = "关联预警ID")
    private Long relatedAlertId;

    @Schema(description = "管控级别：LOW/MEDIUM/HIGH/CRITICAL")
    private String controlLevel;

    @Schema(description = "措施内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "措施内容不能为空")
    private String measureContent;

    @Schema(description = "开始日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "决策理由")
    private String decisionReason;
}
