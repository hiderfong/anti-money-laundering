package com.insurance.aml.module.investigation.controller;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.investigation.model.dto.InvestigationActionRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationOverviewVO;
import com.insurance.aml.module.investigation.model.dto.InvestigationRequestCreateRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationStatusUpdateRequest;
import com.insurance.aml.module.investigation.model.entity.InvestigationAction;
import com.insurance.aml.module.investigation.model.entity.InvestigationRequest;
import com.insurance.aml.module.investigation.service.InvestigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 反洗钱调查协查中心。
 */
@RestController
@RequestMapping("/investigations")
@Tag(name = "调查协查")
@Slf4j
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService investigationService;

    @GetMapping("/overview")
    @Operation(summary = "调查协查概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:view')")
    public Result<InvestigationOverviewVO> overview() {
        return Result.success(investigationService.overview());
    }

    @PostMapping
    @Operation(summary = "创建调查协查请求")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:manage')")
    public Result<InvestigationRequest> createRequest(@Valid @RequestBody InvestigationRequestCreateRequest request) {
        return Result.success(investigationService.createRequest(request));
    }

    @GetMapping
    @Operation(summary = "分页查询调查协查请求")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:view')")
    public Result<PageResult<InvestigationRequest>> pageRequests(
            PageQuery pageQuery,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "请求类型") @RequestParam(required = false) String requestType,
            @Parameter(description = "有权机关") @RequestParam(required = false) String authorityName) {
        return Result.success(investigationService.pageRequests(pageQuery, status, requestType, authorityName));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新调查协查状态")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:manage')")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody InvestigationStatusUpdateRequest request) {
        investigationService.updateStatus(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "登记调查协查动作")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:manage')")
    public Result<InvestigationAction> addAction(@PathVariable Long id, @Valid @RequestBody InvestigationActionRequest request) {
        return Result.success(investigationService.addAction(id, request));
    }

    @GetMapping("/{id}/actions")
    @Operation(summary = "查询调查协查动作记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('investigation:view')")
    public Result<PageResult<InvestigationAction>> pageActions(@PathVariable Long id, PageQuery pageQuery) {
        return Result.success(investigationService.pageActions(id, pageQuery));
    }
}
