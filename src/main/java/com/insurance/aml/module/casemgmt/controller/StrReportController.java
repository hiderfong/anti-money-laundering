package com.insurance.aml.module.casemgmt.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.casemgmt.model.dto.StrReportCreateRequest;
import com.insurance.aml.module.casemgmt.model.dto.StrReportReviewRequest;
import com.insurance.aml.module.casemgmt.service.StrReportService;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 可疑交易报告（STR）控制器
 * 提供可疑交易报告的创建、审核、提交等接口
 */
@RestController
@RequestMapping("/str-reports")
@Tag(name = "可疑交易报告")
@Slf4j
@RequiredArgsConstructor
public class StrReportController {

    private final StrReportService strReportService;

    /**
     * 分页查询STR报告
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询STR报告")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")
    public PageResult<StrReport> pageQuery(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String status) {
        return strReportService.pageQuery(page, size, status);
    }

    /**
     * 创建可疑交易报告
     */
    @PostMapping
    @Operation(summary = "创建可疑交易报告", description = "为案件创建可疑交易报告")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")
    public Result<StrReport> createReport(@Valid @RequestBody StrReportCreateRequest req) {
        log.info("收到创建可疑交易报告请求，caseId={}, reportType={}", req.getCaseId(), req.getReportType());
        StrReport report = strReportService.createReport(req);
        return Result.success(report);
    }

    /**
     * 提交报告审核
     */
    @PostMapping("/{id}/submit-review")
    @Operation(summary = "提交报告审核", description = "将草稿状态的报告提交审核")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")
    public Result<Void> submitForReview(
            @Parameter(description = "报告ID") @PathVariable Long id) {
        log.info("收到提交报告审核请求，reportId={}", id);
        strReportService.submitForReview(id);
        return Result.success();
    }

    /**
     * 审核报告（批准/拒绝）
     */
    @PostMapping("/{id}/review")
    @Operation(summary = "审核报告", description = "对报告进行审核，可批准或拒绝")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    public Result<Void> reviewReport(
            @Parameter(description = "报告ID") @PathVariable Long id,
            @Valid @RequestBody StrReportReviewRequest req) {
        log.info("收到审核报告请求，reportId={}, approved={}", id, req.getApproved());
        req.setReportId(id);
        strReportService.reviewReport(req);
        return Result.success();
    }

    /**
     * 提交报告至监管机构
     */
    @PostMapping("/{id}/submit-regulator")
    @Operation(summary = "提交至监管机构", description = "将已批准的报告提交至监管机构")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    public Result<Void> submitToRegulator(
            @Parameter(description = "报告ID") @PathVariable Long id) {
        log.info("收到提交报告至监管机构请求，reportId={}", id);
        strReportService.submitToRegulator(id);
        return Result.success();
    }

    /**
     * 获取报告详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取报告详情", description = "查询可疑交易报告详细信息")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:str')")
    public Result<StrReport> getReportDetail(
            @Parameter(description = "报告ID") @PathVariable Long id) {
        log.info("收到查询报告详情请求，reportId={}", id);
        StrReport report = strReportService.getReportDetail(id);
        return Result.success(report);
    }
}
