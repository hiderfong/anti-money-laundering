package com.insurance.aml.module.reporting.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 交易统计VO
 */
@Data
public class TransactionStatisticsVO {

    /** 交易总金额 */
    private BigDecimal totalAmount;

    /** 交易总数 */
    private long totalCount;

    /** 大额交易数 */
    private long largeTxnCount;

    /** 可疑交易数 */
    private long suspiciousTxnCount;

    /** 按交易类型分组的金额（transactionType → totalAmount） */
    private Map<String, BigDecimal> byType;
}
