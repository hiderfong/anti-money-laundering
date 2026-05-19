package com.insurance.aml.module.casemgmt.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 案件分页查询请求
 * 继承通用分页参数，增加案件特有过滤条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "案件分页查询请求")
public class CaseQueryRequest extends PageQuery {

    /**
     * 案件状态
     */
    @Schema(description = "案件状态（DRAFT/INVESTIGATING/PENDING_APPROVAL/SUBMITTED/CLOSED）")
    private String caseStatus;

    /**
     * 案件类型
     */
    @Schema(description = "案件类型")
    private String caseType;

    /**
     * 优先级
     */
    @Schema(description = "优先级")
    private Integer priority;

    /**
     * 客户ID
     */
    @Schema(description = "客户ID")
    private Long customerId;

    /**
     * 调查员ID
     */
    @Schema(description = "调查员ID")
    private Long investigatorId;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private String startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private String endTime;
}
