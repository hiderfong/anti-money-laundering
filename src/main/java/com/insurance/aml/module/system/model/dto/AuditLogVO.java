package com.insurance.aml.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志视图对象
 */
@Data
@Schema(description = "审计日志视图对象")
public class AuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 链路追踪ID
     */
    @Schema(description = "链路追踪ID")
    private String traceId;

    /**
     * 操作用户ID
     */
    @Schema(description = "操作用户ID")
    private Long userId;

    /**
     * 操作用户名
     */
    @Schema(description = "操作用户名")
    private String username;

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
     * 操作目标类型
     */
    @Schema(description = "操作目标类型")
    private String targetType;

    /**
     * 操作目标ID
     */
    @Schema(description = "操作目标ID")
    private String targetId;

    /**
     * 操作详情
     */
    @Schema(description = "操作详情")
    private String detail;

    /**
     * 客户端IP地址
     */
    @Schema(description = "客户端IP地址")
    private String ipAddress;

    /**
     * 用户代理信息
     */
    @Schema(description = "用户代理信息")
    private String userAgent;

    /**
     * 请求URI
     */
    @Schema(description = "请求URI")
    private String requestUri;

    /**
     * 请求方法
     */
    @Schema(description = "请求方法")
    private String requestMethod;

    /**
     * 响应状态码
     */
    @Schema(description = "响应状态码")
    private int responseCode;

    /**
     * 请求耗时（毫秒）
     */
    @Schema(description = "请求耗时（毫秒）")
    private Long durationMs;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
