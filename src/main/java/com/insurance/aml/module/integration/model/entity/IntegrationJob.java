package com.insurance.aml.module.integration.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部集成同步任务。
 */
@Data
@TableName("t_integration_job")
public class IntegrationJob extends BaseEntity {
    private String jobCode;
    private String jobName;
    private Long connectorId;
    private String businessObject;
    private String direction;
    private String cronExpression;
    private Integer batchSize;
    private Integer maxRetries;
    private Boolean enabled;
    private String executionStatus;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private String description;

    @TableField(exist = false)
    private String connectorName;
}
