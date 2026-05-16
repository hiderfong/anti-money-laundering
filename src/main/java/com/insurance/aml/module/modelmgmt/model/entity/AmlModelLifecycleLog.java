package com.insurance.aml.module.modelmgmt.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 反洗钱模型生命周期记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_model_lifecycle_log")
public class AmlModelLifecycleLog extends BaseEntity {

    private Long modelId;
    private String modelCode;
    private String actionType;
    private String fromStatus;
    private String toStatus;
    private String operator;
    private LocalDateTime actionTime;
    private String actionSummary;
    private String resultMetric;
    private String artifactRef;
}
