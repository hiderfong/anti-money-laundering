package com.insurance.aml.module.casemgmt.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.casemgmt.model.dto.CaseCreateRequest;
import com.insurance.aml.module.casemgmt.model.dto.CaseDetailVO;
import com.insurance.aml.module.casemgmt.model.dto.CaseQueryRequest;
import com.insurance.aml.module.casemgmt.model.dto.CaseVO;
import com.insurance.aml.module.casemgmt.model.entity.Case;
import com.insurance.aml.module.casemgmt.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 案件管理控制器
 * 提供案件的创建、状态流转、查询、调查记录管理等接口
 */
@RestController
@RequestMapping("/cases")
@Tag(name = "案件管理")
@Slf4j
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    /**
     * 创建案件
     */
    @PostMapping
    @Operation(summary = "创建案件", description = "从已确认的告警创建调查案件")
    @AuditLog(module = "案件管理", operationType = "CREATE", description = "创建案件")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('case:create')")
    public Result<Case> createCase(@Valid @RequestBody CaseCreateRequest req) {
        log.info("收到创建案件请求，alertId={}", req.getAlertId());
        Case caseEntity = caseService.createCase(req);
        return Result.success(caseEntity);
    }

    /**
     * 变更案件状态
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "变更案件状态", description = "变更案件状态，支持状态流转校验")
    @AuditLog(module = "案件管理", operationType = "UPDATE", description = "变更案件状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('case:approve')")
    public Result<Void> changeCaseStatus(
            @Parameter(description = "案件ID") @PathVariable Long id,
            @Parameter(description = "目标状态") @RequestParam String toStatus,
            @Parameter(description = "变更备注") @RequestParam(required = false) String remark) {
        log.info("收到变更案件状态请求，caseId={}, toStatus={}", id, toStatus);
        caseService.changeCaseStatus(id, toStatus, remark);
        return Result.success();
    }

    /**
     * 获取案件详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取案件详情", description = "查询案件完整信息，包含调查记录、附件、STR报告、状态日志")
    public Result<CaseDetailVO> getCaseDetail(
            @Parameter(description = "案件ID") @PathVariable Long id) {
        log.info("收到查询案件详情请求，caseId={}", id);
        CaseDetailVO detailVO = caseService.getCaseDetail(id);
        return Result.success(detailVO);
    }

    /**
     * 分页查询案件列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询案件", description = "按条件分页查询案件列表")
    public Result<PageResult<CaseVO>> pageQueryCases(CaseQueryRequest req) {
        log.info("收到分页查询案件请求，page={}, size={}", req.getPage(), req.getSize());
        PageResult<CaseVO> result = caseService.pageQueryCases(req);
        return Result.success(result);
    }

    /**
     * 添加调查记录
     */
    @PostMapping("/{id}/investigation")
    @Operation(summary = "添加调查记录", description = "为案件添加调查记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('case:create')")
    public Result<Void> addInvestigation(
            @Parameter(description = "案件ID") @PathVariable Long id,
            @Parameter(description = "调查内容") @RequestParam String content,
            @Parameter(description = "调查结论") @RequestParam(required = false) String conclusion) {
        log.info("收到添加调查记录请求，caseId={}", id);
        caseService.addInvestigation(id, content, conclusion);
        return Result.success();
    }

    /**
     * 关闭案件
     */
    @PostMapping("/{id}/close")
    @Operation(summary = "关闭案件", description = "关闭已提交的案件，记录关闭原因")
    @AuditLog(module = "案件管理", operationType = "UPDATE", description = "关闭案件")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('case:approve')")
    public Result<Void> closeCase(
            @Parameter(description = "案件ID") @PathVariable Long id,
            @Parameter(description = "关闭原因") @RequestParam String reason) {
        log.info("收到关闭案件请求，caseId={}, reason={}", id, reason);
        caseService.closeCase(id, reason);
        return Result.success();
    }
}
