package com.insurance.aml.module.alert.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工预警创建请求。
 */
@Data
@Schema(description = "人工预警创建请求参数")
public class ManualAlertCreateRequest {

    @Schema(description = "客户ID")
    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @Schema(description = "客户名称")
    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @Schema(description = "预警类型")
    private String alertType;

    @Schema(description = "风险评分")
    @Min(value = 0, message = "风险评分不能小于0")
    @Max(value = 100, message = "风险评分不能大于100")
    private Integer riskScore;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "命中规则编码")
    private String sourceRuleCodes;

    @Schema(description = "预警摘要")
    @NotBlank(message = "预警摘要不能为空")
    private String alertSummary;

    @Schema(description = "关联交易ID，多个以逗号分隔")
    private String relatedTransactionIds;

    @Schema(description = "命中规则编码")
    private String ruleCode;

    @Schema(description = "命中规则名称")
    private String ruleName;

    @Schema(description = "命中评分")
    private BigDecimal matchScore;

    @Schema(description = "命中详情")
    private String matchDetail;
}
