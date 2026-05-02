package com.insurance.aml.module.monitoring.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易分页查询请求DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "交易分页查询请求")
public class TransactionQueryRequest extends PageQuery {

    /**
     * 客户ID
     */
    @Schema(description = "客户ID")
    private Long customerId;

    /**
     * 保单ID
     */
    @Schema(description = "保单ID")
    private Long policyId;

    /**
     * 交易类型
     */
    @Schema(description = "交易类型")
    private String transactionType;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 最小金额
     */
    @Schema(description = "最小金额")
    private BigDecimal minAmount;

    /**
     * 最大金额
     */
    @Schema(description = "最大金额")
    private BigDecimal maxAmount;

    /**
     * 交易状态
     */
    @Schema(description = "交易状态")
    private String status;
}
