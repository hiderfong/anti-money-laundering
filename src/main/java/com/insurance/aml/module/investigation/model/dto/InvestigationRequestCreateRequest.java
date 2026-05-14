package com.insurance.aml.module.investigation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 调查协查请求创建参数。
 */
@Data
@Schema(description = "调查协查请求创建参数")
public class InvestigationRequestCreateRequest {

    @Schema(description = "有权机关", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "有权机关不能为空")
    private String authorityName;

    @Schema(description = "请求类型：INQUIRY/REVIEW/COPY/ASSIST_INVESTIGATION/OTHER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请求类型不能为空")
    private String requestType;

    @Schema(description = "文书编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文书编号不能为空")
    private String documentNo;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "关联案件ID")
    private Long relatedCaseId;

    @Schema(description = "优先级：LOW/MEDIUM/HIGH/URGENT")
    private String priority;

    @Schema(description = "接收日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "接收日期不能为空")
    private LocalDate receivedDate;

    @Schema(description = "办理期限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "办理期限不能为空")
    private LocalDate dueDate;

    @Schema(description = "经办人")
    private String handler;

    @Schema(description = "请求摘要", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请求摘要不能为空")
    private String summary;
}
