package com.insurance.aml.module.screening.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 筛查请求DTO
 */
@Data
@Schema(description = "筛查请求参数")
public class ScreeningRequestDTO {

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long customerId;

    /**
     * 筛查类型
     */
    @NotBlank(message = "筛查类型不能为空")
    @Schema(description = "筛查类型（CUSTOMER_ONBOARD/INFO_CHANGE/TRANSACTION/PERIODIC/BATCH）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String screeningType;
}
