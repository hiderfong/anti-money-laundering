package com.insurance.aml.module.system.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogQueryRequest extends PageQuery {

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 操作用户名（模糊查询）
     */
    private String username;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 所属模块
     */
    private String module;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;
}
