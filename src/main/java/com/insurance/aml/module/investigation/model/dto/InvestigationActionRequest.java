package com.insurance.aml.module.investigation.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调查协查动作登记参数。
 */
@Data
@Schema(description = "调查协查动作登记参数")
public class InvestigationActionRequest {

    @Schema(description = "动作类型：INQUIRY/REVIEW/COPY/DATA_EXPORT/RESPONSE/OTHER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    @Schema(description = "动作内容", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "动作内容不能为空")
    private String actionContent;

    @Schema(description = "动作结果")
    private String actionResult;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "动作时间")
    private LocalDateTime actionTime;

    @Schema(description = "附件或证据引用")
    private String attachmentRef;
}
