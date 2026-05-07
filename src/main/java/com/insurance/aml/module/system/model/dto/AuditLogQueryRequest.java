package com.insurance.aml.module.system.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审计日志查询请求参数")
public class AuditLogQueryRequest extends PageQuery {

    /**
     * 操作用户ID
     */
    @Schema(description = "操作用户ID")
    private Long userId;

    /**
     * 操作用户名（模糊查询）
     */
    @Schema(description = "操作用户名，模糊查询")
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
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
