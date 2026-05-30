package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * AI评分跟进任务创建请求。
 */
@Data
@Schema(description = "AI评分跟进任务创建请求")
public class AiRiskFollowUpTaskRequest {

    @Schema(description = "任务类型：RECTIFICATION-整改核查，MONITORING-持续监控")
    private String taskType;

    @Schema(description = "问题分类")
    private String issueCategory;

    @Schema(description = "严重程度：HIGH/MEDIUM/LOW")
    private String severity;

    @Schema(description = "责任部门")
    private String responsibleDept;

    @Schema(description = "责任人")
    private String responsiblePerson;

    @Schema(description = "整改或跟进期限")
    private LocalDate deadline;

    @Schema(description = "补充说明")
    private String comment;
}
