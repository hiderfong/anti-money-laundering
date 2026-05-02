package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 整改任务请求DTO
 */
@Data
@Schema(description = "整改任务请求")
public class RectificationTaskRequest {

    @Schema(description = "评估ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评估ID不能为空")
    private Long assessmentId;

    @Schema(description = "问题描述", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题描述不能为空")
    private String issueDescription;

    @Schema(description = "严重程度（HIGH/MEDIUM/LOW）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "严重程度不能为空")
    private String severity;

    @Schema(description = "责任部门")
    private String responsibleDept;

    @Schema(description = "责任人")
    private String responsiblePerson;

    @Schema(description = "整改期限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "整改期限不能为空")
    private LocalDate deadline;
}
