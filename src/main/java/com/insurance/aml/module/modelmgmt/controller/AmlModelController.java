package com.insurance.aml.module.modelmgmt.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.modelmgmt.model.dto.ModelCreateRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelLifecycleRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelOverviewVO;
import com.insurance.aml.module.modelmgmt.model.dto.ModelQueryRequest;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModelLifecycleLog;
import com.insurance.aml.module.modelmgmt.service.AmlModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反洗钱模型全生命周期管理。
 */
@RestController
@RequestMapping("/models")
@Tag(name = "模型管理")
@Slf4j
@RequiredArgsConstructor
public class AmlModelController {

    private final AmlModelService modelService;

    @GetMapping("/overview")
    @Operation(summary = "模型管理概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<ModelOverviewVO> overview() {
        return Result.success(modelService.overview());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<PageResult<AmlModel>> pageModels(@Valid ModelQueryRequest request) {
        return Result.success(modelService.pageModels(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "模型详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<AmlModel> getModel(@Parameter(description = "模型ID") @PathVariable Long id) {
        return Result.success(modelService.getModel(id));
    }

    @PostMapping
    @Operation(summary = "创建模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "CREATE", description = "创建反洗钱模型")
    public Result<AmlModel> createModel(@Valid @RequestBody ModelCreateRequest request) {
        log.info("创建反洗钱模型，modelCode={}", request.getModelCode());
        return Result.success(modelService.createModel(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "UPDATE", description = "更新反洗钱模型")
    public Result<AmlModel> updateModel(
            @Parameter(description = "模型ID") @PathVariable Long id,
            @Valid @RequestBody ModelCreateRequest request) {
        return Result.success(modelService.updateModel(id, request));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "测试模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "TEST", description = "登记模型测试结果")
    public Result<AmlModel> testModel(@PathVariable Long id, @RequestBody(required = false) ModelLifecycleRequest request) {
        return Result.success(modelService.testModel(id, normalize(request)));
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "部署模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "DEPLOY", description = "登记模型部署")
    public Result<AmlModel> deployModel(@PathVariable Long id, @RequestBody(required = false) ModelLifecycleRequest request) {
        return Result.success(modelService.deployModel(id, normalize(request)));
    }

    @PostMapping("/{id}/monitor")
    @Operation(summary = "刷新模型监控")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "MONITOR", description = "刷新模型监控指标")
    public Result<AmlModel> monitorModel(@PathVariable Long id, @RequestBody(required = false) ModelLifecycleRequest request) {
        return Result.success(modelService.monitorModel(id, normalize(request)));
    }

    @PostMapping("/{id}/iterate")
    @Operation(summary = "模型迭代")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "ITERATE", description = "登记模型迭代")
    public Result<AmlModel> iterateModel(@PathVariable Long id, @RequestBody(required = false) ModelLifecycleRequest request) {
        return Result.success(modelService.iterateModel(id, normalize(request)));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档模型")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:manage')")
    @AuditLog(module = "模型管理", operationType = "ARCHIVE", description = "归档反洗钱模型")
    public Result<AmlModel> archiveModel(@PathVariable Long id, @RequestBody(required = false) ModelLifecycleRequest request) {
        return Result.success(modelService.archiveModel(id, normalize(request)));
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "模型生命周期记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<PageResult<AmlModelLifecycleLog>> pageLifecycleLogs(@PathVariable Long id, PageQuery pageQuery) {
        return Result.success(modelService.pageLifecycleLogs(id, pageQuery));
    }

    private ModelLifecycleRequest normalize(ModelLifecycleRequest request) {
        return request == null ? new ModelLifecycleRequest() : request;
    }
}
