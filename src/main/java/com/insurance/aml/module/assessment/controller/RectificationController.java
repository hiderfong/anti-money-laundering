package com.insurance.aml.module.assessment.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;
import com.insurance.aml.module.assessment.service.RectificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 整改任务控制器
 * 提供整改任务的创建、状态更新、查询、验证等接口
 */
@RestController
@RequestMapping("/assessments/rectifications")
@Tag(name = "整改任务")
@Slf4j
@RequiredArgsConstructor
public class RectificationController {

    private final RectificationService rectificationService;

    /**
     * 创建整改任务
     */
    @PostMapping
    @Operation(summary = "创建整改任务", description = "为评估问题创建整改任务")
    public Result<RectificationTask> createTask(@Valid @RequestBody RectificationTaskRequest req) {
        log.info("创建整改任务请求，assessmentId={}, severity={}", req.getAssessmentId(), req.getSeverity());
        RectificationTask task = rectificationService.createTask(req);
        return Result.success(task);
    }

    /**
     * 更新整改任务状态
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "更新任务状态", description = "更新整改任务的状态")
    public Result<Void> updateTaskStatus(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Parameter(description = "新状态（OPEN/IN_PROGRESS/COMPLETED/OVERDUE）") @RequestParam String status) {
        log.info("更新整改任务状态请求，taskId={}, status={}", id, status);
        rectificationService.updateTaskStatus(id, status);
        return Result.success();
    }

    /**
     * 查询整改任务列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询整改任务", description = "按评估ID查询整改任务列表，支持逾期自动检测")
    public Result<List<RectificationTask>> listTasks(
            @Parameter(description = "评估ID（可选）") @RequestParam(required = false) Long assessmentId) {
        List<RectificationTask> tasks = rectificationService.listTasks(assessmentId);
        return Result.success(tasks);
    }

    /**
     * 验证整改任务
     */
    @PostMapping("/{id}/verify")
    @Operation(summary = "验证整改任务", description = "验证已完成整改任务的完成情况")
    public Result<Void> verifyTask(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Parameter(description = "验证人") @RequestParam String verifiedBy) {
        log.info("验证整改任务请求，taskId={}, verifiedBy={}", id, verifiedBy);
        rectificationService.verifyTask(id, verifiedBy);
        return Result.success();
    }
}
