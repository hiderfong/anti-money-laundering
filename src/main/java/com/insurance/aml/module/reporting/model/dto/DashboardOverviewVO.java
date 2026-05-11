package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘概览数据VO
 */
@Data
@Schema(description = "仪表盘概览视图对象")
public class DashboardOverviewVO {

    @Schema(description = "客户总数")
    private long totalCustomers;

    @Schema(description = "高风险客户数")
    private long highRiskCustomers;

    @Schema(description = "中风险客户数")
    private long mediumRiskCustomers;

    @Schema(description = "低风险客户数")
    private long lowRiskCustomers;

    @Schema(description = "告警总数")
    private long totalAlerts;

    @Schema(description = "新建告警数")
    private long newAlerts;

    @Schema(description = "处理中告警数")
    private long processingAlerts;

    @Schema(description = "已确认告警数")
    private long confirmedAlerts;

    @Schema(description = "案件总数")
    private long totalCases;

    @Schema(description = "待处理案件数")
    private long openCases;

    @Schema(description = "已关闭案件数")
    private long closedCases;

    @Schema(description = "交易总数")
    private long totalTransactions;

    @Schema(description = "交易总金额")
    private BigDecimal totalTransactionAmount;

    @Schema(description = "大额交易报告数")
    private long largeTxnReports;

    @Schema(description = "已提交报告数")
    private long submittedReports;

    @Schema(description = "待处理报告数")
    private long pendingReports;

    @Schema(description = "近期告警趋势")
    private List<DailyTrendVO> recentAlertTrend;
}
