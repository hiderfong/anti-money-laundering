package com.insurance.aml.module.monitoring.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易录入请求DTO
 */
@Data
@Schema(description = "交易录入请求")
public class TransactionIngestRequest {

    /**
     * 交易流水号
     */
    @NotBlank(message = "交易流水号不能为空")
    @Schema(description = "交易流水号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String transactionNo;

    /**
     * 保单ID
     */
    @Schema(description = "保单ID")
    private Long policyId;

    /**
     * 客户ID
     */
    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long customerId;

    /**
     * 交易类型
     */
    @NotBlank(message = "交易类型不能为空")
    @Schema(description = "交易类型", allowableValues = {"PREMIUM", "SURRENDER", "CLAIM", "LOAN", "REPAYMENT", "PARTIAL_WITHDRAWAL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String transactionType;

    /**
     * 交易金额
     */
    @NotNull(message = "交易金额不能为空")
    @Schema(description = "交易金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /**
     * 币种（默认CNY）
     */
    @Schema(description = "币种", example = "CNY")
    private String currency;

    /**
     * 支付方式
     */
    @Schema(description = "支付方式", allowableValues = {"CASH", "TRANSFER", "BANK_CARD", "CHECK", "OTHER"})
    private String paymentMethod;

    /**
     * 交易渠道
     */
    @Schema(description = "交易渠道")
    private String channel;

    /**
     * 交易对手名称
     */
    @Schema(description = "交易对手名称")
    private String counterpartyName;

    /**
     * 交易对手账号
     */
    @Schema(description = "交易对手账号")
    private String counterpartyAccount;

    /**
     * 交易对手开户行
     */
    @Schema(description = "交易对手开户行")
    private String counterpartyBank;

    /**
     * 是否跨境交易
     */
    @Schema(description = "是否跨境交易")
    private Boolean isCrossBorder;

    /**
     * 交易时间
     */
    @NotNull(message = "交易时间不能为空")
    @Schema(description = "交易时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime transactionTime;
}
