package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 交易统计VO
 */
@Data
@Schema(description = "交易统计视图对象")
public class TransactionStatisticsVO {

    @Schema(description = "交易总金额")
    private BigDecimal totalAmount;

    @Schema(description = "交易总数")
    private long totalCount;

    @Schema(description = "大额交易数")
    private long largeTxnCount;

    @Schema(description = "可疑交易数")
    private long suspiciousTxnCount;

    @Schema(description = "按交易类型分组的金额")
    private Map<String, BigDecimal> byType;
}
