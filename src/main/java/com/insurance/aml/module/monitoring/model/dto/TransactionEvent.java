package com.insurance.aml.module.monitoring.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易事件DTO
 * 用于Kafka消息传递，作为Producer和Consumer之间的数据契约
 */
@Data
public class TransactionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交易ID */
    private Long id;

    /** 交易流水号 */
    private String transactionNo;

    /** 保单ID */
    private Long policyId;

    /** 客户ID */
    private Long customerId;

    /** 交易类型: PREMIUM/SURRENDER/CLAIM/LOAN/REPAYMENT/PARTIAL_WITHDRAWAL */
    private String transactionType;

    /** 交易金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 支付方式: CASH/TRANSFER/BANK_CARD/CHECK/OTHER */
    private String paymentMethod;

    /** 交易渠道 */
    private String channel;

    /** 交易对手名称 */
    private String counterpartyName;

    /** 交易对手账号 */
    private String counterpartyAccount;

    /** 交易对手开户行 */
    private String counterpartyBank;

    /** 是否跨境交易 */
    private Boolean isCrossBorder;

    /** 交易时间 */
    private LocalDateTime transactionTime;

    /** 备注 */
    private String remark;

    /** 交易状态: SUCCESS/FAILED/PENDING/REVERSED */
    private String status;

    /** 来源系统 */
    private String sourceSystem;

    /** 事件发送时间 */
    private LocalDateTime eventTime;

    /**
     * 从Transaction实体构建TransactionEvent
     */
    public static TransactionEvent fromEntity(com.insurance.aml.module.monitoring.model.entity.Transaction transaction) {
        TransactionEvent event = new TransactionEvent();
        event.setId(transaction.getId());
        event.setTransactionNo(transaction.getTransactionNo());
        event.setPolicyId(transaction.getPolicyId());
        event.setCustomerId(transaction.getCustomerId());
        event.setTransactionType(transaction.getTransactionType());
        event.setAmount(transaction.getAmount());
        event.setCurrency(transaction.getCurrency());
        event.setPaymentMethod(transaction.getPaymentMethod());
        event.setChannel(transaction.getChannel());
        event.setCounterpartyName(transaction.getCounterpartyName());
        event.setCounterpartyAccount(transaction.getCounterpartyAccount());
        event.setCounterpartyBank(transaction.getCounterpartyBank());
        event.setIsCrossBorder(transaction.getIsCrossBorder());
        event.setTransactionTime(transaction.getTransactionTime());
        event.setRemark(transaction.getRemark());
        event.setStatus(transaction.getStatus());
        event.setSourceSystem(transaction.getSourceSystem());
        event.setEventTime(LocalDateTime.now());
        return event;
    }
}
