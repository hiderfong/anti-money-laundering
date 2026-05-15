package com.insurance.aml.module.monitoring.model.dto;

import com.insurance.aml.common.annotation.MaskField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易视图对象
 */
@Data
@Schema(description = "交易详情")
public class TransactionVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "交易流水号")
    private String transactionNo;

    @Schema(description = "保单ID")
    private Long policyId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "交易类型")
    private String transactionType;

    @Schema(description = "交易金额")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "支付方式")
    private String paymentMethod;

    @Schema(description = "交易渠道")
    private String channel;

    @Schema(description = "交易对手名称")
    private String counterpartyName;

    @MaskField(MaskField.MaskType.BANK_ACCOUNT)
    @Schema(description = "交易对手账号")
    private String counterpartyAccount;

    @Schema(description = "交易对手开户行")
    private String counterpartyBank;

    @Schema(description = "是否跨境交易")
    private Boolean isCrossBorder;

    @Schema(description = "交易时间")
    private LocalDateTime transactionTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "交易状态")
    private String status;

    @Schema(description = "来源系统")
    private String sourceSystem;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
