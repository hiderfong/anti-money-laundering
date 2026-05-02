package com.insurance.aml.module.alert.model.dto;

import com.insurance.aml.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预警分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertQueryRequest extends PageQuery {

    /** 预警类型 */
    private String alertType;

    /** 风险等级 */
    private String riskLevel;

    /** 预警状态 */
    private String status;

    /** 分配处理人ID */
    private Long assignedTo;

    /** 客户ID */
    private Long customerId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
