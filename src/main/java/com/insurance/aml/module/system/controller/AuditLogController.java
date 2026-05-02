package com.insurance.aml.module.system.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.system.model.dto.AuditLogQueryRequest;
import com.insurance.aml.module.system.model.dto.AuditLogVO;
import com.insurance.aml.module.system.service.AuditLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 审计日志控制器
 * 提供审计日志查询和导出接口
 */
@Slf4j
@RestController
@RequestMapping("/system/audit-logs")
@Tag(name = "审计日志", description = "审计日志查询相关接口")
public class AuditLogController {

    @Autowired
    private AuditLogQueryService auditLogQueryService;

    /**
     * 分页查询审计日志
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询日志", description = "支持按用户ID、用户名、操作类型、模块、时间范围筛选")
    public Result<PageResult<AuditLogVO>> pageQueryLogs(AuditLogQueryRequest req) {
        log.debug("分页查询审计日志");
        PageResult<AuditLogVO> result = auditLogQueryService.pageQueryLogs(req);
        return Result.success(result);
    }

    /**
     * 获取审计日志详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "日志详情", description = "获取指定审计日志的详细信息")
    public Result<AuditLogVO> getLogDetail(
            @Parameter(description = "日志ID") @PathVariable Long id) {
        AuditLogVO vo = auditLogQueryService.getLogDetail(id);
        return Result.success(vo);
    }

    /**
     * 导出审计日志为CSV文件
     */
    @GetMapping("/export")
    @Operation(summary = "导出日志", description = "将审计日志导出为CSV文件下载")
    public ResponseEntity<byte[]> exportLogs(AuditLogQueryRequest req) {
        log.info("导出审计日志CSV请求");
        byte[] csvBytes = auditLogQueryService.exportLogs(req);

        String filename = URLEncoder.encode("审计日志.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }
}
