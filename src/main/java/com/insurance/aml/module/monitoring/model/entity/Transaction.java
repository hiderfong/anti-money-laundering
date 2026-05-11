package com.insurance.aml.module.monitoring.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import com.insurance.aml.common.enums.TransactionStatus;

import java.time.LocalDateTime;

/**
 * 交易实体
 * 记录所有保险交易信息，用于反洗钱监测
 */
@Data
@TableName("t_transaction")
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（雪花算法生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 交易流水号
     */
    private String transactionNo;

    /**
     * 保单ID
     */
    private Long policyId;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 交易类型
     * PREMIUM-保费缴纳, SURRENDER-退保, CLAIM-理赔,
     * LOAN-保单贷款, REPAYMENT-贷款还款, PARTIAL_WITHDRAWAL-部分领取
     */
    private String transactionType;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 币种（默认人民币）
     */
    private String currency = "CNY";

    /**
     * 支付方式
     * CASH-现金, TRANSFER-转账, BANK_CARD-银行卡,
     * CHECK-支票, OTHER-其他
     */
    private String paymentMethod;

    /**
     * 交易渠道
     */
    private String channel;

    /**
     * 交易对手名称
     */
    private String counterpartyName;

    /**
     * 交易对手账号
     */
    private String counterpartyAccount;

    /**
     * 交易对手开户行
     */
    private String counterpartyBank;

    /**
     * 是否跨境交易（默认否）
     */
    private Boolean isCrossBorder = false;

    /**
     * 交易时间
     */
    private LocalDateTime transactionTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 交易状态
     * SUCCESS-成功, FAILED-失败, PENDING-待处理, REVERSED-已冲正
     */
    private String status = TransactionStatus.SUCCESS.getCode();

    /**
     * 来源系统
     */
    private String sourceSystem;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
