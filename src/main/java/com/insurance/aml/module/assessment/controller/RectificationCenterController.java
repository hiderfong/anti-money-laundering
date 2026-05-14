package com.insurance.aml.module.assessment.controller;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.assessment.model.dto.RectificationProgressRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationVerifyRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.module.assessment.service.RectificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 独立整改中心。
 */
@RestController
@RequestMapping("/rectifications")
@Tag(name = "整改中心")
@Slf4j
@RequiredArgsConstructor
public class RectificationCenterController {

    private final RectificationService rectificationService;

    @PostMapping
    @Operation(summary = "创建整改任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('rectification:manage')")
    public Result<RectificationTask> createTask(@Valid @RequestBody RectificationTaskRequest req) {
        return Result.success(rectificationService.createTask(req));
    }

    @GetMapping
    @Operation(summary = "分页查询整改任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('rectification:view')")
    public Result<PageResult<RectificationTask>> pageTasks(
            PageQuery pageQuery,
            @Parameter(description = "问题来源") @RequestParam(required = false) String sourceType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "责任人") @RequestParam(required = false) String responsiblePerson) {
        return Result.success(rectificationService.pageTasks(pageQuery, sourceType, status, responsiblePerson));
    }

    @PutMapping("/{id}/progress")
    @Operation(summary = "更新整改进度")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('rectification:manage')")
    public Result<Void> updateProgress(@PathVariable Long id, @Valid @RequestBody RectificationProgressRequest req) {
        rectificationService.updateProgress(id, req);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新整改任务状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('rectification:manage')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        rectificationService.updateTaskStatus(id, status);
        return Result.success();
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "验证整改任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('rectification:manage')")
    public Result<Void> verifyTask(@PathVariable Long id, @Valid @RequestBody RectificationVerifyRequest req) {
        rectificationService.verifyTask(id, req);
        return Result.success();
    }
}
