package com.insurance.aml.module.reporting.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘概览数据VO
 */
@Data
public class DashboardOverviewVO {

    /** 客户总数 */
    private long totalCustomers;

    /** 高风险客户数 */
    private long highRiskCustomers;

    /** 中风险客户数 */
    private long mediumRiskCustomers;

    /** 低风险客户数 */
    private long lowRiskCustomers;

    /** 告警总数 */
    private long totalAlerts;

    /** 新建告警数 */
    private long newAlerts;

    /** 处理中告警数 */
    private long processingAlerts;

    /** 已确认告警数 */
    private long confirmedAlerts;

    /** 案件总数 */
    private long totalCases;

    /** 待处理案件数 */
    private long openCases;

    /** 已关闭案件数 */
    private long closedCases;

    /** 交易总数 */
    private long totalTransactions;

    /** 交易总金额 */
    private BigDecimal totalTransactionAmount;

    /** 大额交易报告数 */
    private long largeTxnReports;

    /** 已提交报告数 */
    private long submittedReports;

    /** 待处理报告数 */
    private long pendingReports;

    /** 近期告警趋势 */
    private List<DailyTrendVO> recentAlertTrend;
}
