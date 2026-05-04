package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志 ES 全文检索请求
 */
@Data
@Schema(description = "审计日志全文检索请求")
public class AuditLogSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全文检索关键词（搜索 detail、errorMessage、username 等字段）
     */
    @Schema(description = "全文检索关键词")
    private String keyword;

    /**
     * 操作用户ID
     */
    @Schema(description = "操作用户ID")
    private Long userId;

    /**
     * 操作类型
     */
    @Schema(description = "操作类型")
    private String operationType;

    /**
     * 所属模块
     */
    @Schema(description = "所属模块")
    private String module;

    /**
     * 请求URI
     */
    @Schema(description = "请求URI")
    private String requestUri;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 当前页码（从1开始）
     */
    @Schema(description = "当前页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    /**
     * 每页条数
     */
    @Schema(description = "每页条数", example = "20")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 500, message = "每页条数最大为500")
    private int size = 20;
}
