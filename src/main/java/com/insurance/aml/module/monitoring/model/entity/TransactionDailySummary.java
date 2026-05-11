package com.insurance.aml.module.monitoring.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易日汇总实体
 * 按客户、日期、交易类型汇总交易数据，用于大额交易和可疑交易监测
 */
@Data
@TableName("t_transaction_daily_summary")
public class TransactionDailySummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 汇总日期
     */
    private LocalDate summaryDate;

    /**
     * 交易类型
     */
    private String transactionType;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 是否跨境交易
     */
    private Boolean isCrossBorder;

    /**
     * 累计交易金额
     */
    private BigDecimal totalAmount;

    /**
     * 交易笔数
     */
    private Integer transactionCount;

    /**
     * 是否触发大额交易标志
     */
    private Boolean largeTxnFlag;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
