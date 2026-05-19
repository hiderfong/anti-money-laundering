package com.insurance.aml.module.casemgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建案件请求
 */
@Data
@Schema(description = "创建案件请求")
public class CaseCreateRequest {

    /**
     * 关联告警ID（必填）
     */
    @NotNull(message = "告警ID不能为空")
    @Schema(description = "关联告警ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long alertId;

    /**
     * 案件类型
     */
    @Schema(description = "案件类型")
    private String caseType;

    /**
     * 优先级（1-5，5最高）
     */
    @Schema(description = "优先级（1-5）", example = "3")
    private Integer priority;

    /**
     * 案件摘要
     */
    @Schema(description = "案件摘要")
    private String summary;
}
