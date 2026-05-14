package com.insurance.aml.module.prevention.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 查冻扣记录创建请求。
 */
@Data
@Schema(description = "查冻扣记录创建请求")
public class FreezeSeizureDeductionRequest {

    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "有权机关", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "有权机关不能为空")
    private String authorityName;

    @Schema(description = "文书编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文书编号不能为空")
    private String documentNo;

    @Schema(description = "类型：QUERY/FREEZE/SEIZURE/DEDUCTION", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "查冻扣类型不能为空")
    private String actionType;

    @Schema(description = "涉及金额")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "生效日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    @Schema(description = "到期日期")
    private LocalDate expiryDate;

    @Schema(description = "经办人")
    private String handler;

    @Schema(description = "备注")
    private String remark;
}
