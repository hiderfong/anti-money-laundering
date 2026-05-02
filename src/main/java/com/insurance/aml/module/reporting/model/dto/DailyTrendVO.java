package com.insurance.aml.module.reporting.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 每日趋势数据VO
 */
@Data
public class DailyTrendVO {

    /** 日期，格式 yyyy-MM-dd */
    private String date;

    /** 数量 */
    private long count;

    /** 金额 */
    private BigDecimal amount;
}
