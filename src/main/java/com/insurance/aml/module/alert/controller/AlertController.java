package com.insurance.aml.module.alert.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.alert.model.dto.*;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
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
     * 获取预警处置链路
     */
    @GetMapping("/{id}/disposition-chain")
    @Operation(summary = "预警处置链路", description = "聚合预警关联交易、案件、STR报告和报送状态")
    public Result<AlertDispositionChainVO> getDispositionChain(
            @Parameter(description = "预警ID", required = true) @PathVariable Long id) {
        log.debug("接收到获取预警处置链路请求，预警ID：{}", id);
        return Result.success(alertService.getDispositionChain(id));
    }

    /**
     * 人工创建预警
     */
    @PostMapping("/manual")
    @Operation(summary = "人工创建预警", description = "管理员或预警处理人根据线索人工创建预警")
    @AuditLog(module = "预警管理", operationType = "CREATE", description = "人工创建预警")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('alert:process')")
    public Result<AlertVO> createManualAlert(@Valid @RequestBody ManualAlertCreateRequest request) {
        log.info("接收到人工创建预警请求，客户ID：{}，客户名称：{}", request.getCustomerId(), request.getCustomerName());
        Alert alert = buildManualAlert(request);
        AlertRuleDetail detail = buildManualAlertRuleDetail(request);
        Alert created = alertService.createAlert(alert, detail == null ? Collections.emptyList() : List.of(detail));
        return Result.success(alertService.getAlertDetail(created.getId()));
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

    private Alert buildManualAlert(ManualAlertCreateRequest request) {
        Alert alert = new Alert();
        alert.setCustomerId(request.getCustomerId());
        alert.setCustomerName(request.getCustomerName());
        alert.setAlertType(StringUtils.hasText(request.getAlertType()) ? request.getAlertType() : "MANUAL");
        alert.setRiskScore(request.getRiskScore() == null ? 80 : request.getRiskScore());
        alert.setRiskLevel(StringUtils.hasText(request.getRiskLevel()) ? request.getRiskLevel() : "HIGH");
        alert.setSourceRuleCodes(StringUtils.hasText(request.getSourceRuleCodes())
                ? request.getSourceRuleCodes()
                : "MANUAL_REVIEW");
        alert.setAlertSummary(request.getAlertSummary());
        alert.setRelatedTransactionIds(request.getRelatedTransactionIds());
        alert.setDeduplicateKey("MANUAL:" + request.getCustomerId() + ":" + System.currentTimeMillis());
        return alert;
    }

    private AlertRuleDetail buildManualAlertRuleDetail(ManualAlertCreateRequest request) {
        if (!StringUtils.hasText(request.getRuleCode())
                && !StringUtils.hasText(request.getRuleName())
                && !StringUtils.hasText(request.getMatchDetail())) {
            return null;
        }
        AlertRuleDetail detail = new AlertRuleDetail();
        detail.setRuleCode(StringUtils.hasText(request.getRuleCode()) ? request.getRuleCode() : "MANUAL_REVIEW");
        detail.setRuleName(StringUtils.hasText(request.getRuleName()) ? request.getRuleName() : "人工复核线索");
        detail.setMatchScore(request.getMatchScore() == null ? BigDecimal.valueOf(100) : request.getMatchScore());
        detail.setMatchDetail(request.getMatchDetail());
        return detail;
    }
}
