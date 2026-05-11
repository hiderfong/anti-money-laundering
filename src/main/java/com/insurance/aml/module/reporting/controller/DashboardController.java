package com.insurance.aml.module.reporting.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.alert.model.dto.AlertVO;
import com.insurance.aml.module.alert.service.AlertService;
import com.insurance.aml.module.kyc.model.dto.CustomerVO;
import com.insurance.aml.module.kyc.service.CustomerService;
import com.insurance.aml.module.reporting.model.dto.*;
import com.insurance.aml.module.reporting.service.DashboardService;
import com.insurance.aml.module.reporting.service.ExportService;
import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import com.insurance.aml.module.monitoring.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仪表盘控制器
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "仪表盘")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final ExportService exportService;
    private final AlertService alertService;
    private final TransactionService transactionService;
    private final CustomerService customerService;

    @GetMapping("/overview")
    @Operation(summary = "获取仪表盘概览数据")
    public Result<DashboardOverviewVO> getOverview() {
        DashboardOverviewVO overview = dashboardService.getOverview();
        return Result.success(overview);
    }

    @GetMapping("/alert-trend")
    @Operation(summary = "获取告警趋势")
    public Result<List<DailyTrendVO>> getAlertTrend(
            @Parameter(description = "天数，默认30天")
            @RequestParam(defaultValue = "30") int days) {
        List<DailyTrendVO> trend = dashboardService.getAlertTrend(days);
        return Result.success(trend);
    }

    @GetMapping("/alert-statistics")
    @Operation(summary = "获取告警统计")
    public Result<List<AlertStatisticsVO>> getAlertStatistics() {
        List<AlertStatisticsVO> statistics = dashboardService.getAlertStatistics();
        return Result.success(statistics);
    }

    @GetMapping("/kyc-statistics")
    @Operation(summary = "获取KYC统计")
    public Result<KycStatisticsVO> getKycStatistics() {
        KycStatisticsVO statistics = dashboardService.getKycStatistics();
        return Result.success(statistics);
    }

    @GetMapping("/transaction-statistics")
    @Operation(summary = "获取交易统计")
    public Result<TransactionStatisticsVO> getTransactionStatistics() {
        TransactionStatisticsVO statistics = dashboardService.getTransactionStatistics();
        return Result.success(statistics);
    }

    /*
    // TODO: 导出功能需要在各Service中添加listAll()方法
    @GetMapping("/export/alerts")
    @Operation(summary = "导出告警数据")
    public ResponseEntity<byte[]> exportAlerts() {
        List<AlertVO> alerts = alertService.listAll();
        byte[] data = exportService.exportAlertsToExcel(alerts);
        return buildCsvResponse(data, "alerts.csv");
    }

    @GetMapping("/export/transactions")
    @Operation(summary = "导出交易数据")
    public ResponseEntity<byte[]> exportTransactions() {
        List<TransactionVO> transactions = transactionService.listAll();
        byte[] data = exportService.exportTransactionsToExcel(transactions);
        return buildCsvResponse(data, "transactions.csv");
    }

    @GetMapping("/export/customers")
    @Operation(summary = "导出客户数据")
    public ResponseEntity<byte[]> exportCustomers() {
        List<CustomerVO> customers = customerService.listAll();
        byte[] data = exportService.exportCustomersToExcel(customers);
        return buildCsvResponse(data, "customers.csv");
    }
    */

    /**
     * 构建CSV文件下载响应
     */
    private ResponseEntity<byte[]> buildCsvResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(data);
    }
}
