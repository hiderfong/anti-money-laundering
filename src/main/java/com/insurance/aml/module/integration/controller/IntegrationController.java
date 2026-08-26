package com.insurance.aml.module.integration.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationJobQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationJobRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationOverviewVO;
import com.insurance.aml.module.integration.model.dto.IntegrationRunQuery;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;
import com.insurance.aml.module.integration.model.entity.IntegrationRun;
import com.insurance.aml.module.integration.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部系统集成中心接口。
 */
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
@Tag(name = "集成中心")
public class IntegrationController {
    private final IntegrationService integrationService;

    @GetMapping("/overview")
    @Operation(summary = "查询集成运行概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<IntegrationOverviewVO> overview() {
        return Result.success(integrationService.overview());
    }

    @GetMapping("/connectors/page")
    @Operation(summary = "分页查询连接器")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<PageResult<IntegrationConnector>> pageConnectors(@Valid IntegrationConnectorQuery query) {
        return Result.success(integrationService.pageConnectors(query));
    }

    @GetMapping("/connectors/enabled")
    @Operation(summary = "查询启用的连接器")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<List<IntegrationConnector>> enabledConnectors() {
        return Result.success(integrationService.listEnabledConnectors());
    }

    @PostMapping("/connectors")
    @Operation(summary = "创建连接器")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:manage')")
    @AuditLog(module = "集成中心", operationType = "CREATE", description = "创建外部系统连接器")
    public Result<IntegrationConnector> createConnector(@Valid @RequestBody IntegrationConnectorRequest request) {
        return Result.success(integrationService.createConnector(request));
    }

    @PutMapping("/connectors/{id}")
    @Operation(summary = "更新连接器")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:manage')")
    @AuditLog(module = "集成中心", operationType = "UPDATE", description = "更新外部系统连接器")
    public Result<IntegrationConnector> updateConnector(@PathVariable Long id,
            @Valid @RequestBody IntegrationConnectorRequest request) {
        return Result.success(integrationService.updateConnector(id, request));
    }

    @PostMapping("/connectors/{id}/test")
    @Operation(summary = "测试连接器")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:execute')")
    @AuditLog(module = "集成中心", operationType = "TEST", description = "测试外部系统连接")
    public Result<IntegrationRun> testConnection(@PathVariable Long id) {
        return Result.success(integrationService.testConnection(id));
    }

    @GetMapping("/jobs/page")
    @Operation(summary = "分页查询集成任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<PageResult<IntegrationJob>> pageJobs(@Valid IntegrationJobQuery query) {
        return Result.success(integrationService.pageJobs(query));
    }

    @PostMapping("/jobs")
    @Operation(summary = "创建集成任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:manage')")
    @AuditLog(module = "集成中心", operationType = "CREATE", description = "创建外部集成任务")
    public Result<IntegrationJob> createJob(@Valid @RequestBody IntegrationJobRequest request) {
        return Result.success(integrationService.createJob(request));
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "更新集成任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:manage')")
    @AuditLog(module = "集成中心", operationType = "UPDATE", description = "更新外部集成任务")
    public Result<IntegrationJob> updateJob(@PathVariable Long id, @Valid @RequestBody IntegrationJobRequest request) {
        return Result.success(integrationService.updateJob(id, request));
    }

    @PostMapping("/jobs/{id}/run")
    @Operation(summary = "立即执行集成任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:execute')")
    @AuditLog(module = "集成中心", operationType = "EXECUTE", description = "手动执行外部集成任务")
    public Result<IntegrationRun> triggerJob(@PathVariable Long id) {
        return Result.success(integrationService.triggerJob(id));
    }

    @GetMapping("/runs/page")
    @Operation(summary = "分页查询运行记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<PageResult<IntegrationRun>> pageRuns(@Valid IntegrationRunQuery query) {
        return Result.success(integrationService.pageRuns(query));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "查询运行详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:view')")
    public Result<IntegrationRun> runDetail(@PathVariable Long id) {
        return Result.success(integrationService.getRun(id));
    }

    @PostMapping("/runs/{id}/retry")
    @Operation(summary = "重试失败运行")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('integration:execute')")
    @AuditLog(module = "集成中心", operationType = "RETRY", description = "重试失败的外部集成任务")
    public Result<IntegrationRun> retryRun(@PathVariable Long id) {
        return Result.success(integrationService.retryRun(id));
    }
}
