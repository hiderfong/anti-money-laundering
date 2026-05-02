package com.insurance.aml.module.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.case_.mapper.CaseMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.model.dto.*;
import com.insurance.aml.module.reporting.service.DashboardService;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CustomerMapper customerMapper;
    private final AlertMapper alertMapper;
    private final CaseMapper caseMapper;
    private final TransactionMapper transactionMapper;
    private final LargeTxnReportMapper largeTxnReportMapper;

    // 告警状态常量
    private static final String ALERT_STATUS_NEW = "NEW";
    private static final String ALERT_STATUS_PROCESSING = "PROCESSING";
    private static final String ALERT_STATUS_CONFIRMED = "CONFIRMED";

    // 案件状态常量
    private static final String CASE_STATUS_OPEN = "OPEN";
    private static final String CASE_STATUS_CLOSED = "CLOSED";

    // KYC状态常量
    private static final String KYC_STATUS_COMPLETE = "COMPLETED";
    private static final String KYC_STATUS_INCOMPLETE = "INCOMPLETE";
    private static final String KYC_STATUS_REVIEWING = "REVIEWING";

    // 风险等级常量
    private static final String RISK_LEVEL_LOW = "LOW";
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";
    private static final String RISK_LEVEL_HIGH = "HIGH";

    // 报告状态常量
    private static final String REPORT_STATUS_SUBMITTED = "SUBMITTED";
    private static final String REPORT_STATUS_PENDING = "PENDING";

    @Override
    public DashboardOverviewVO getOverview() {
        log.info("获取仪表盘概览数据");
        DashboardOverviewVO overview = new DashboardOverviewVO();

        // 客户统计
        overview.setTotalCustomers(countCustomers(null));
        overview.setHighRiskCustomers(countCustomersByRiskLevel(RISK_LEVEL_HIGH));
        overview.setMediumRiskCustomers(countCustomersByRiskLevel(RISK_LEVEL_MEDIUM));
        overview.setLowRiskCustomers(countCustomersByRiskLevel(RISK_LEVEL_LOW));

        // 告警统计
        overview.setTotalAlerts(countAlerts(null));
        overview.setNewAlerts(countAlertsByStatus(ALERT_STATUS_NEW));
        overview.setProcessingAlerts(countAlertsByStatus(ALERT_STATUS_PROCESSING));
        overview.setConfirmedAlerts(countAlertsByStatus(ALERT_STATUS_CONFIRMED));

        // 案件统计
        overview.setTotalCases(countCases(null));
        overview.setOpenCases(countCasesByStatus(CASE_STATUS_OPEN));
        overview.setClosedCases(countCasesByStatus(CASE_STATUS_CLOSED));

        // 交易统计
        overview.setTotalTransactions(countTransactions());
        overview.setTotalTransactionAmount(sumTransactionAmount());

        // 大额交易报告统计
        overview.setLargeTxnReports(countLargeTxnReports(null));
        overview.setSubmittedReports(countLargeTxnReportsByStatus(REPORT_STATUS_SUBMITTED));
        overview.setPendingReports(countLargeTxnReportsByStatus(REPORT_STATUS_PENDING));

        // 近30天告警趋势
        overview.setRecentAlertTrend(getAlertTrend(30));

        return overview;
    }

    @Override
    public List<DailyTrendVO> getAlertTrend(int days) {
        log.info("获取最近{}天告警趋势", days);
        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        // 查询最近N天的告警
        List<Alert> alerts = alertMapper.selectList(
                new LambdaQueryWrapper<Alert>()
                        .ge(Alert::getCreatedTime, startDateTime)
                        .orderByAsc(Alert::getCreatedTime)
        );

        // 按日期分组统计
        Map<String, List<Alert>> grouped = alerts.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCreatedTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // 填充所有日期（包括无告警的日期）
        List<DailyTrendVO> result = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            DailyTrendVO vo = new DailyTrendVO();
            vo.setDate(dateStr);
            List<Alert> dayAlerts = grouped.getOrDefault(dateStr, Collections.emptyList());
            vo.setCount(dayAlerts.size());
            vo.setAmount(BigDecimal.ZERO);
            result.add(vo);
        }

        return result;
    }

    @Override
    public List<AlertStatisticsVO> getAlertStatistics() {
        log.info("获取告警统计");

        // 查询所有告警
        List<Alert> allAlerts = alertMapper.selectList(null);
        long total = allAlerts.size();

        if (total == 0) {
            return Collections.emptyList();
        }

        // 按告警类型分组
        Map<String, Long> grouped = allAlerts.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAlertType() != null ? a.getAlertType() : "UNKNOWN",
                        Collectors.counting()
                ));

        List<AlertStatisticsVO> result = new ArrayList<>();
        grouped.forEach((type, count) -> {
            AlertStatisticsVO vo = new AlertStatisticsVO();
            vo.setAlertType(type);
            vo.setCount(count);
            vo.setPercentage(Math.round(count * 10000.0 / total) / 100.0);
            result.add(vo);
        });

        // 按数量降序排列
        result.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return result;
    }

    @Override
    public KycStatisticsVO getKycStatistics() {
        log.info("获取KYC统计");
        KycStatisticsVO vo = new KycStatisticsVO();

        // 客户总数
        vo.setTotalCustomers(countCustomers(null));

        // 按KYC状态统计
        vo.setKycComplete(countCustomersByKycStatus(KYC_STATUS_COMPLETE));
        vo.setKycIncomplete(countCustomersByKycStatus(KYC_STATUS_INCOMPLETE));
        vo.setKycReviewing(countCustomersByKycStatus(KYC_STATUS_REVIEWING));

        // 风险等级分布
        Map<String, Long> riskDistribution = new HashMap<>();
        riskDistribution.put(RISK_LEVEL_LOW, countCustomersByRiskLevel(RISK_LEVEL_LOW));
        riskDistribution.put(RISK_LEVEL_MEDIUM, countCustomersByRiskLevel(RISK_LEVEL_MEDIUM));
        riskDistribution.put(RISK_LEVEL_HIGH, countCustomersByRiskLevel(RISK_LEVEL_HIGH));
        vo.setRiskDistribution(riskDistribution);

        return vo;
    }

    @Override
    public TransactionStatisticsVO getTransactionStatistics() {
        log.info("获取交易统计");
        TransactionStatisticsVO vo = new TransactionStatisticsVO();

        // 总计
        vo.setTotalCount(countTransactions());
        vo.setTotalAmount(sumTransactionAmount());

        // 大额交易数
        vo.setLargeTxnCount(countLargeTxnReports(null));

        // 可疑交易数（这里使用已确认的告警数作为近似）
        vo.setSuspiciousTxnCount(countAlertsByStatus(ALERT_STATUS_CONFIRMED));

        // 按交易类型分组统计金额
        List<Transaction> allTxns = transactionMapper.selectList(null);
        Map<String, BigDecimal> byType = allTxns.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTransactionType() != null ? t.getTransactionType() : "UNKNOWN",
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
        vo.setByType(byType);

        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 统计客户数量（可选风险等级过滤）
     */
    private long countCustomers(String riskLevel) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (riskLevel != null) {
            wrapper.eq(Customer::getRiskLevel, riskLevel);
        }
        return customerMapper.selectCount(wrapper);
    }

    /**
     * 按风险等级统计客户数
     */
    private long countCustomersByRiskLevel(String riskLevel) {
        return customerMapper.selectCount(
                new LambdaQueryWrapper<Customer>().eq(Customer::getRiskLevel, riskLevel)
        );
    }

    /**
     * 按KYC状态统计客户数
     */
    private long countCustomersByKycStatus(String kycStatus) {
        return customerMapper.selectCount(
                new LambdaQueryWrapper<Customer>().eq(Customer::getKycStatus, kycStatus)
        );
    }

    /**
     * 统计告警数量（可选状态过滤）
     */
    private long countAlerts(String status) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Alert::getStatus, status);
        }
        return alertMapper.selectCount(wrapper);
    }

    /**
     * 按状态统计告警数
     */
    private long countAlertsByStatus(String status) {
        return alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, status)
        );
    }

    /**
     * 统计案件数量（可选状态过滤）
     */
    private long countCases(String status) {
        LambdaQueryWrapper<Case> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Case::getCaseStatus, status);
        }
        return caseMapper.selectCount(wrapper);
    }

    /**
     * 按状态统计案件数
     */
    private long countCasesByStatus(String status) {
        return caseMapper.selectCount(
                new LambdaQueryWrapper<Case>().eq(Case::getCaseStatus, status)
        );
    }

    /**
     * 统计交易总数
     */
    private long countTransactions() {
        return transactionMapper.selectCount(null);
    }

    /**
     * 统计交易总金额
     */
    private BigDecimal sumTransactionAmount() {
        List<Transaction> transactions = transactionMapper.selectList(null);
        return transactions.stream()
                .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 统计大额交易报告数
     */
    private long countLargeTxnReports(String status) {
        LambdaQueryWrapper<LargeTxnReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(LargeTxnReport::getReportStatus, status);
        }
        return largeTxnReportMapper.selectCount(wrapper);
    }

    /**
     * 按状态统计大额交易报告数
     */
    private long countLargeTxnReportsByStatus(String status) {
        return largeTxnReportMapper.selectCount(
                new LambdaQueryWrapper<LargeTxnReport>().eq(LargeTxnReport::getReportStatus, status)
        );
    }
}
