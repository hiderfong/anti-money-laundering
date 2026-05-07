package com.insurance.aml.module.alert.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预警分配请求
 */
@Data
@Schema(description = "预警分配请求参数")
public class AlertAssignRequest {

    @Schema(description = "预警ID")
    @NotNull(message = "预警ID不能为空")
    private Long alertId;

    @Schema(description = "分配给的用户ID")
    @NotNull(message = "分配人不能为空")
    private Long assignTo;

    @Schema(description = "分配原因")
    private String assignReason;
}
