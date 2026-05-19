package com.insurance.aml.module.prevention.controller;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.prevention.model.dto.*;
import com.insurance.aml.module.prevention.model.entity.FreezeSeizureDeduction;
import com.insurance.aml.module.prevention.model.entity.RetrospectiveScreeningJob;
import com.insurance.aml.module.prevention.model.entity.SpecialMeasure;
import com.insurance.aml.module.prevention.model.entity.WatchlistUpdateJob;
import com.insurance.aml.module.prevention.service.SpecialPreventionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 特别预防措施中心。
 */
@RestController
@RequestMapping("/special-prevention")
@Tag(name = "特别预防措施")
@Slf4j
@RequiredArgsConstructor
public class SpecialPreventionController {

    private final SpecialPreventionService service;

    @GetMapping("/overview")
    @Operation(summary = "特别预防概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:view')")
    public Result<SpecialPreventionOverviewVO> overview() {
        return Result.success(service.overview());
    }

    @PostMapping("/watchlist-update-jobs")
    @Operation(summary = "创建名单更新任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<WatchlistUpdateJob> createWatchlistUpdateJob(@Valid @RequestBody WatchlistSyncRequest request) {
        return Result.success(service.createWatchlistUpdateJob(request));
    }

    @GetMapping("/watchlist-update-jobs")
    @Operation(summary = "分页查询名单更新任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:view')")
    public Result<PageResult<WatchlistUpdateJob>> pageWatchlistUpdateJobs(
            PageQuery pageQuery,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return Result.success(service.pageWatchlistUpdateJobs(pageQuery, status));
    }

    @PostMapping("/retrospective-jobs")
    @Operation(summary = "创建回溯筛查任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<RetrospectiveScreeningJob> createRetrospectiveJob(@Valid @RequestBody RetrospectiveScreeningJobRequest request) {
        return Result.success(service.createRetrospectiveJob(request));
    }

    @GetMapping("/retrospective-jobs")
    @Operation(summary = "分页查询回溯筛查任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:view')")
    public Result<PageResult<RetrospectiveScreeningJob>> pageRetrospectiveJobs(
            PageQuery pageQuery,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return Result.success(service.pageRetrospectiveJobs(pageQuery, status));
    }

    @PostMapping("/measures")
    @Operation(summary = "创建特别预防措施")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<SpecialMeasure> createSpecialMeasure(@Valid @RequestBody SpecialMeasureRequest request) {
        return Result.success(service.createSpecialMeasure(request));
    }

    @GetMapping("/measures")
    @Operation(summary = "分页查询特别预防措施")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:view')")
    public Result<PageResult<SpecialMeasure>> pageSpecialMeasures(
            PageQuery pageQuery,
            @Parameter(description = "客户ID") @RequestParam(required = false) Long customerId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return Result.success(service.pageSpecialMeasures(pageQuery, customerId, status));
    }

    @PutMapping("/measures/{id}/status")
    @Operation(summary = "更新特别预防措施状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<Void> updateSpecialMeasureStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        service.updateSpecialMeasureStatus(id, status, reason);
        return Result.success();
    }

    @PostMapping("/freeze-records")
    @Operation(summary = "创建查冻扣记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<FreezeSeizureDeduction> createFreezeRecord(@Valid @RequestBody FreezeSeizureDeductionRequest request) {
        return Result.success(service.createFreezeRecord(request));
    }

    @GetMapping("/freeze-records")
    @Operation(summary = "分页查询查冻扣记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:view')")
    public Result<PageResult<FreezeSeizureDeduction>> pageFreezeRecords(
            PageQuery pageQuery,
            @Parameter(description = "客户ID") @RequestParam(required = false) Long customerId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return Result.success(service.pageFreezeRecords(pageQuery, customerId, status));
    }

    @PutMapping("/freeze-records/{id}/status")
    @Operation(summary = "更新查冻扣记录状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<Void> updateFreezeRecordStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String remark) {
        service.updateFreezeRecordStatus(id, status, remark);
        return Result.success();
    }

    @PostMapping("/screening-results/{resultId}/escalate-alert")
    @Operation(summary = "将筛查命中升级为预警")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<Alert> escalateScreeningResultToAlert(
            @PathVariable Long resultId,
            @RequestParam(required = false) String reason) {
        return Result.success(service.escalateScreeningResultToAlert(resultId, reason));
    }

    @PostMapping("/screening-results/{resultId}/create-case")
    @Operation(summary = "将筛查命中升级为案件")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('special:manage')")
    public Result<Case> createCaseFromScreeningResult(
            @PathVariable Long resultId,
            @RequestParam(required = false) String reason) {
        return Result.success(service.createCaseFromScreeningResult(resultId, reason));
    }
}
