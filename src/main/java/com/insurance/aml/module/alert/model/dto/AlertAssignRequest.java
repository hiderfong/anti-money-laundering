package com.insurance.aml.module.alert.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预警分配请求
 */
@Data
public class AlertAssignRequest {

    /** 预警ID */
    @NotNull(message = "预警ID不能为空")
    private Long alertId;

    /** 分配给的用户ID */
    @NotNull(message = "分配人不能为空")
    private Long assignTo;

    /** 分配原因 */
    private String assignReason;
}
