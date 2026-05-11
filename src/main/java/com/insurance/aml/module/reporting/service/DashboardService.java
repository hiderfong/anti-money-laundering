package com.insurance.aml.module.reporting.service;

import com.insurance.aml.module.reporting.model.dto.*;

import java.util.List;

/**
 * 仪表盘服务接口
 */
public interface DashboardService {

    /**
     * 获取仪表盘概览数据
     */
    DashboardOverviewVO getOverview();

    /**
     * 获取告警趋势（最近N天）
     *
     * @param days 天数
     */
    List<DailyTrendVO> getAlertTrend(int days);

    /**
     * 获取告警统计（按类型分组）
     */
    List<AlertStatisticsVO> getAlertStatistics();

    /**
     * 获取KYC统计
     */
    KycStatisticsVO getKycStatistics();

    /**
     * 获取交易统计
     */
    TransactionStatisticsVO getTransactionStatistics();
}
