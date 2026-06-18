package com.insurance.aml.module.assessment.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.assessment.model.dto.AssessmentCreateRequest;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreRequest;
import com.insurance.aml.module.assessment.model.dto.SelfAssessmentDetailVO;
import com.insurance.aml.module.assessment.model.entity.SelfAssessment;
import com.insurance.aml.module.assessment.service.SelfAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 风险自评估控制器
 * 提供创建评估、提交评分、完成评估、审批评估等接口
 */
@RestController
@RequestMapping("/assessments")
@Tag(name = "风险自评估")
@Slf4j
@RequiredArgsConstructor
public class SelfAssessmentController {

    private final SelfAssessmentService assessmentService;

    /**
     * 创建风险自评估
     */
    @PostMapping
    @Operation(summary = "创建风险自评估", description = "创建新的风险自评估任务")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:manage')")
    public Result<SelfAssessment> createAssessment(@Valid @RequestBody AssessmentCreateRequest req) {
        log.info("创建风险自评估请求，year={}, period={}", req.getAssessmentYear(), req.getAssessmentPeriod());
        SelfAssessment assessment = assessmentService.createAssessment(req);
        return Result.success(assessment);
    }

    /**
     * 提交指标评分
     */
    @PostMapping("/score")
    @Operation(summary = "提交指标评分", description = "为评估提交指标的评分数据")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:manage')")
    public Result<Void> submitScore(@Valid @RequestBody AssessmentScoreRequest req) {
        log.info("提交评估评分请求，assessmentId={}, indicatorId={}", req.getAssessmentId(), req.getIndicatorId());
        assessmentService.submitScore(req);
        return Result.success();
    }

    /**
     * 完成评估
     */
    @PostMapping("/{id}/complete")
    @Operation(summary = "完成评估", description = "计算综合评分和风险等级，完成评估")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:manage')")
    public Result<SelfAssessment> completeAssessment(
            @Parameter(description = "评估ID") @PathVariable Long id) {
        log.info("完成评估请求，assessmentId={}", id);
        SelfAssessment assessment = assessmentService.completeAssessment(id);
        return Result.success(assessment);
    }

    /**
     * 审批评估
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "审批评估", description = "审批通过已完成的评估")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:manage')")
    public Result<Void> approveAssessment(
            @Parameter(description = "评估ID") @PathVariable Long id,
            @Parameter(description = "审批人") @RequestParam String approvedBy) {
        log.info("审批评估请求，assessmentId={}, approvedBy={}", id, approvedBy);
        assessmentService.approveAssessment(id, approvedBy);
        return Result.success();
    }

    /**
     * 获取评估详情（含评分明细和指标信息）
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取评估详情", description = "根据ID获取评估详细信息，包含评分明细和指标详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:view')")
    public Result<SelfAssessmentDetailVO> getAssessmentDetail(
            @Parameter(description = "评估ID") @PathVariable Long id) {
        SelfAssessmentDetailVO detail = assessmentService.getAssessmentDetail(id);
        return Result.success(detail);
    }

    /**
     * 查询评估列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询评估列表", description = "按年度查询风险自评估列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('assessment:view')")
    public Result<List<SelfAssessment>> listAssessments(
            @Parameter(description = "评估年度") @RequestParam(required = false) Integer year) {
        List<SelfAssessment> list = assessmentService.listAssessments(year);
        return Result.success(list);
    }
}
