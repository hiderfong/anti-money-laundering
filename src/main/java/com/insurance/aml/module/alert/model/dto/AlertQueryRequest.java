package com.insurance.aml.module.alert.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预警分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "预警查询请求参数")
public class AlertQueryRequest extends PageQuery {

    @Schema(description = "预警类型")
    private String alertType;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "预警状态")
    private String status;

    @Schema(description = "分配处理人ID")
    private Long assignedTo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
