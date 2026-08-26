package com.insurance.aml.module.integration.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集成任务运行记录，承载健康检查、手动执行、调度执行和重试审计。
 */
@Data
@TableName("t_integration_run")
public class IntegrationRun extends BaseEntity {
    private String runNo;
    private Long jobId;
    private Long connectorId;
    private Long retryOfRunId;
    private String triggerType;
    private String status;
    private Integer attemptCount;
    private Integer retryCount;
    private Integer recordsRead;
    private Integer recordsWritten;
    private Integer recordsSkipped;
    private Integer errorCount;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private Long durationMs;
    private String requestSummary;
    private String responseSummary;
    private String errorMessage;
    private String traceId;
    private String executedBy;

    @TableField(exist = false)
    private String jobName;

    @TableField(exist = false)
    private String connectorName;
}
