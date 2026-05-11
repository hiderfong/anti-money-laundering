package com.insurance.aml.module.monitoring.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易评估上下文
 * 封装交易数据及补充信息，作为Drools规则的事实对象
 * 包含数据库规则难以直接获取的统计类字段
 */
@Data
public class TransactionEvaluationContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始交易对象
     */
    private Transaction transaction;

    /**
     * 最近N秒内的交易笔数（用于高频交易检测）
     * 由服务层从数据库查询后注入
     */
    private Integer recentTransactionCount = 0;

    /**
     * 统计时间窗口（秒），默认120秒
     */
    private Integer recentTimeWindowSeconds = 120;

    /**
     * 客户当日累计交易金额
     */
    private BigDecimal dailyCumulativeAmount = BigDecimal.ZERO;

    /**
     * 客户当日交易笔数
     */
    private Integer dailyTransactionCount = 0;

    // ====== 便捷方法，供Drools规则直接调用 ======

    public BigDecimal getAmount() {
        return transaction != null ? transaction.getAmount() : BigDecimal.ZERO;
    }

    public String getPaymentMethod() {
        return transaction != null ? transaction.getPaymentMethod() : null;
    }

    public Boolean getIsCrossBorder() {
        return transaction != null ? transaction.getIsCrossBorder() : false;
    }

    public String getTransactionType() {
        return transaction != null ? transaction.getTransactionType() : null;
    }

    public String getCurrency() {
        return transaction != null ? transaction.getCurrency() : "CNY";
    }

    public String getChannel() {
        return transaction != null ? transaction.getChannel() : null;
    }

    public LocalDateTime getTransactionTime() {
        return transaction != null ? transaction.getTransactionTime() : null;
    }

    public Long getCustomerId() {
        return transaction != null ? transaction.getCustomerId() : null;
    }

    public Long getTransactionId() {
        return transaction != null ? transaction.getId() : null;
    }

    public String getTransactionNo() {
        return transaction != null ? transaction.getTransactionNo() : null;
    }
}
