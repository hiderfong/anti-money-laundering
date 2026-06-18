package com.insurance.aml.module.reporting.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportQueryRequest;
import com.insurance.aml.module.reporting.model.dto.LargeTxnReportVO;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.service.LargeTxnReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 监管报送控制器
 * 提供大额交易报告的生成、审核、提交和查询接口
 */
@Slf4j
@RestController
@RequestMapping("/reporting")
@RequiredArgsConstructor
@Tag(name = "监管报送", description = "监管报送相关接口")
public class ReportingController {
    private final LargeTxnReportService largeTxnReportService;

    /**
     * 生成大额交易报告
     */
    @PostMapping("/large-txn/generate")
    @Operation(summary = "生成大额交易报告", description = "根据交易ID生成大额交易报告草稿")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")
    public Result<LargeTxnReport> generateReport(
            @Parameter(description = "交易ID", required = true) @RequestParam Long transactionId) {
        log.info("接收到生成大额交易报告请求，交易ID：{}", transactionId);
        LargeTxnReport report = largeTxnReportService.generateReport(transactionId);
        return Result.success(report);
    }

    /**
     * 审核报告
     */
    @PostMapping("/large-txn/{id}/review")
    @Operation(summary = "审核大额交易报告", description = "审核指定的大额交易报告")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    public Result<Void> reviewReport(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id,
            @Parameter(description = "审核人", required = true) @RequestParam String reviewedBy) {
        log.info("接收到审核大额交易报告请求，报告ID：{}，审核人：{}", id, reviewedBy);
        largeTxnReportService.reviewReport(id, reviewedBy);
        return Result.success();
    }

    /**
     * 提交报告
     */
    @PostMapping("/large-txn/{id}/submit")
    @Operation(summary = "提交大额交易报告", description = "将审核通过的报告提交至监管机构")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    public Result<Void> submitReport(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        log.info("接收到提交大额交易报告请求，报告ID：{}", id);
        largeTxnReportService.submitReport(id);
        return Result.success();
    }

    /**
     * 分页查询报告
     */
    @GetMapping("/large-txn/page")
    @Operation(summary = "分页查询报告", description = "根据条件分页查询大额交易报告列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")
    public Result<PageResult<LargeTxnReportVO>> pageQueryReports(LargeTxnReportQueryRequest request) {
        log.debug("接收到分页查询大额交易报告请求");
        PageResult<LargeTxnReportVO> result = largeTxnReportService.pageQueryReports(request);
        return Result.success(result);
    }

    /**
     * 预览XML报文
     */
    @GetMapping("/large-txn/{id}/xml")
    @Operation(summary = "预览XML报文", description = "预览指定报告的XML报文内容")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")
    public Result<String> previewXml(
            @Parameter(description = "报告ID", required = true) @PathVariable Long id) {
        log.debug("接收到预览XML报文请求，报告ID：{}", id);
        String xml = largeTxnReportService.generateXml(id);
        return Result.success(xml);
    }

    /**
     * 重试失败的提交
     */
    @PostMapping("/large-txn/retry-failed")
    @Operation(summary = "重试失败提交", description = "重试所有提交失败的大额交易报告")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    public Result<Void> retryFailedSubmissions() {
        log.info("接收到重试失败提交请求");
        largeTxnReportService.retryFailedSubmissions();
        return Result.success();
    }
}
