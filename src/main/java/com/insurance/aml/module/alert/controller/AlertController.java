package com.insurance.aml.module.alert.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.alert.model.dto.*;
import com.insurance.aml.module.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 预警管理控制器
 * 提供预警查询、分配、处理等接口
 */
@Slf4j
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Tag(name = "预警管理", description = "预警查询、分配、处理相关接口")
public class AlertController {
    private final AlertService alertService;

    /**
     * 分页查询预警列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预警", description = "根据条件分页查询预警列表")
    public Result<PageResult<AlertVO>> pageQueryAlerts(AlertQueryRequest request) {
        log.debug("接收到分页查询预警请求");
        PageResult<AlertVO> result = alertService.pageQueryAlerts(request);
        return Result.success(result);
    }

    /**
     * 获取预警详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取预警详情", description = "根据预警ID获取预警详细信息及命中规则明细")
    public Result<AlertVO> getAlertDetail(
            @Parameter(description = "预警ID", required = true) @PathVariable Long id) {
        log.debug("接收到获取预警详情请求，预警ID：{}", id);
        AlertVO alertVO = alertService.getAlertDetail(id);
        return Result.success(alertVO);
    }

    /**
     * 分配预警
     */
    @PostMapping("/assign")
    @Operation(summary = "分配预警", description = "将预警分配给指定处理人")
    @AuditLog(module = "预警管理", operationType = "UPDATE", description = "分配预警")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:process')")
    public Result<Void> assignAlert(@Valid @RequestBody AlertAssignRequest request) {
        log.info("接收到分配预警请求，预警ID：{}，分配给：{}", request.getAlertId(), request.getAssignTo());
        alertService.assignAlert(request);
        return Result.success();
    }

    /**
     * 处理预警
     */
    @PostMapping("/process")
    @Operation(summary = "处理预警", description = "处理预警，确认可疑/排除/升级")
    @AuditLog(module = "预警管理", operationType = "UPDATE", description = "处理预警")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:process')")
    public Result<Void> processAlert(@Valid @RequestBody AlertProcessRequest request) {
        log.info("接收到处理预警请求，预警ID：{}，处理结果：{}", request.getAlertId(), request.getProcessResult());
        alertService.processAlert(request);
        return Result.success();
    }

    /**
     * 批量处理预警
     */
    @PostMapping("/batch-process")
    @Operation(summary = "批量处理预警", description = "批量处理多个预警")
    @AuditLog(module = "预警管理", operationType = "UPDATE", description = "批量处理预警")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:process')")
    public Result<Void> batchProcess(@RequestBody BatchProcessRequest request) {
        log.info("接收到批量处理预警请求，数量：{}，动作：{}", request.getAlertIds().size(), request.getAction());
        alertService.batchProcess(request.getAlertIds(), request.getAction());
        return Result.success();
    }

    /**
     * 获取预警统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "预警统计", description = "获取预警按状态、风险等级的统计数据")
    public Result<AlertStatisticsVO> getAlertStatistics() {
        log.debug("接收到预警统计请求");
        // 统计逻辑直接在此处实现，避免过度设计
        AlertStatisticsVO statistics = alertService.getAlertStatistics();
        return Result.success(statistics);
    }

    // ==================== 内部请求/响应类 ====================

    /**
     * 批量处理请求
     */
    @lombok.Data
    public static class BatchProcessRequest {
        /** 预警ID列表 */
        private List<Long> alertIds;
        /** 处理动作（CONFIRMED_SUSPICIOUS/EXCLUDED/ESCALATED） */
        private String action;
    }

    /**
     * 预警统计VO
     */
    @lombok.Data
    public static class AlertStatisticsVO {
        /** 各状态数量统计 */
        private java.util.Map<String, Long> countByStatus;
        /** 各风险等级数量统计 */
        private java.util.Map<String, Long> countByRiskLevel;
        /** 预警总数 */
        private long totalCount;
    }
}
