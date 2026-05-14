package com.insurance.aml.module.assessment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 整改任务请求DTO
 */
@Data
@Schema(description = "整改任务请求")
public class RectificationTaskRequest {

    @Schema(description = "评估ID，自评估来源时填写")
    private Long assessmentId;

    @Schema(description = "问题来源：SELF_ASSESSMENT/INTERNAL_CHECK/EXTERNAL_CHECK/REGULATOR/AUDIT")
    private String sourceType;

    @Schema(description = "来源业务ID")
    private Long sourceId;

    @Schema(description = "问题描述", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "问题描述不能为空")
    private String issueDescription;

    @Schema(description = "问题分类")
    private String issueCategory;

    @Schema(description = "严重程度（HIGH/MEDIUM/LOW）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "严重程度不能为空")
    private String severity;

    @Schema(description = "责任部门")
    private String responsibleDept;

    @Schema(description = "责任人")
    private String responsiblePerson;

    @Schema(description = "整改期限", requiredMode = Schema.RequiredMode.REQUIRED)
    @jakarta.validation.constraints.NotNull(message = "整改期限不能为空")
    private LocalDate deadline;
}
