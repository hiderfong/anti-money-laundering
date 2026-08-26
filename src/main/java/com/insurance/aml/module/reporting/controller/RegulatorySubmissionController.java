package com.insurance.aml.module.reporting.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.reporting.model.dto.RegulatoryReceiptRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatoryResubmitRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionDetailVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionOverviewVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionQuery;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmitRequest;
import com.insurance.aml.module.reporting.model.entity.RegulatorySubmission;
import com.insurance.aml.module.reporting.service.RegulatorySubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reporting/submissions")
@RequiredArgsConstructor
@Tag(name = "监管报送工作台")
public class RegulatorySubmissionController {
    private final RegulatorySubmissionService submissionService;

    @GetMapping("/overview")
    @Operation(summary = "查询监管报送概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")
    public Result<RegulatorySubmissionOverviewVO> overview() {
        return Result.success(submissionService.overview());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询监管报送版本")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")
    public Result<PageResult<RegulatorySubmission>> page(@Valid RegulatorySubmissionQuery query) {
        return Result.success(submissionService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询监管报送证据链详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:view')")
    public Result<RegulatorySubmissionDetailVO> detail(@PathVariable Long id) {
        return Result.success(submissionService.detail(id));
    }

    @PostMapping
    @Operation(summary = "生成、校验、签名并提交监管报告")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    @AuditLog(module = "监管报送", operationType = "SUBMIT", description = "生成签名报文并提交监管网关")
    public Result<RegulatorySubmission> submit(@Valid @RequestBody RegulatorySubmitRequest request) {
        return Result.success(submissionService.submitInitial(
                request.getReportType(), request.getReportId(), request.getConnectorId()));
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "根据退回原因创建新版本并重报")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    @AuditLog(module = "监管报送", operationType = "RESUBMIT", description = "修正并重报监管报告")
    public Result<RegulatorySubmission> resubmit(@PathVariable Long id,
            @Valid @RequestBody RegulatoryResubmitRequest request) {
        return Result.success(submissionService.resubmit(id, request));
    }

    @PostMapping("/{id}/poll-receipt")
    @Operation(summary = "主动查询监管回执")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    @AuditLog(module = "监管报送", operationType = "POLL", description = "主动查询监管回执")
    public Result<RegulatorySubmission> pollReceipt(@PathVariable Long id) {
        return Result.success(submissionService.pollReceipt(id));
    }

    @PostMapping("/{id}/receipt")
    @Operation(summary = "接收监管回执回调")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('report:submit')")
    @AuditLog(module = "监管报送", operationType = "RECEIPT", description = "登记监管回执")
    public Result<RegulatorySubmission> receipt(@PathVariable Long id,
            @RequestParam(defaultValue = "CALLBACK") String source,
            @Valid @RequestBody RegulatoryReceiptRequest request) {
        return Result.success(submissionService.applyReceipt(id, request, source));
    }
}
