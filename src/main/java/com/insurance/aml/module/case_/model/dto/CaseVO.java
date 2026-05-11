package com.insurance.aml.module.case_.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案件视图对象
 * 用于案件列表查询结果展示
 */
@Data
@Schema(description = "案件视图对象")
public class CaseVO {

    @Schema(description = "案件ID")
    private Long id;

    @Schema(description = "案件编号")
    private String caseNo;

    @Schema(description = "关联告警ID")
    private Long alertId;

    @Schema(description = "告警编号")
    private String alertNo;

    @Schema(description = "关联客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;

    @Schema(description = "案件状态")
    private String caseStatus;

    @Schema(description = "案件类型")
    private String caseType;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "案件摘要")
    private String summary;

    @Schema(description = "调查员ID")
    private Long investigatorId;

    @Schema(description = "审核人ID")
    private Long reviewerId;

    @Schema(description = "审批人ID")
    private Long approverId;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "关闭时间")
    private LocalDateTime closeTime;

    @Schema(description = "关闭原因")
    private String closeReason;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新人")
    private String updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    /**
     * 调查记录数
     */
    @Schema(description = "调查记录数")
    private Integer investigationCount;

    /**
     * 是否有可疑交易报告
     */
    @Schema(description = "是否有可疑交易报告")
    private boolean hasStrReport;
}
