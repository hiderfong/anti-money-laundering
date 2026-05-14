package com.insurance.aml.module.prevention.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 回溯筛查任务创建请求。
 */
@Data
@Schema(description = "回溯筛查任务创建请求")
public class RetrospectiveScreeningJobRequest {

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务名称不能为空")
    private String jobName;

    @Schema(description = "筛查范围：ALL_CUSTOMERS/HIGH_RISK/ACTIVE_CUSTOMERS/CUSTOMER_IDS", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "筛查范围不能为空")
    private String scopeType;

    @Schema(description = "指定客户ID列表，逗号分隔")
    private String customerIds;

    @Schema(description = "名单源ID")
    private Long watchlistSourceId;

    @Schema(description = "备注")
    private String remark;
}
