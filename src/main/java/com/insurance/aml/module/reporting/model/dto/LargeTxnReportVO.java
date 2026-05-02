package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 大额交易报告视图对象
 */
@Data
@Schema(description = "大额交易报告详情")
public class LargeTxnReportVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "报告编号")
    private String reportNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "交易ID")
    private Long transactionId;

    @Schema(description = "报告日期")
    private LocalDate reportDate;

    @Schema(description = "交易时间")
    private LocalDateTime transactionTime;

    @Schema(description = "交易类型")
    private String transactionType;

    @Schema(description = "交易金额")
    private BigDecimal amount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "支付方式")
    private String paymentMethod;

    @Schema(description = "交易对手信息")
    private String counterpartyInfo;

    @Schema(description = "报告状态")
    private String reportStatus;

    @Schema(description = "审核人")
    private String reviewedBy;

    @Schema(description = "审核时间")
    private LocalDateTime reviewedTime;

    @Schema(description = "提交人")
    private String submittedBy;

    @Schema(description = "提交时间")
    private LocalDateTime submittedTime;

    @Schema(description = "XML报文内容")
    private String xmlContent;

    @Schema(description = "提交响应结果")
    private String submitResponse;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
