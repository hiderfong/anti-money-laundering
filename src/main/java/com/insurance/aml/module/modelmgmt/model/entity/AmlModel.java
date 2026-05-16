package com.insurance.aml.module.modelmgmt.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 反洗钱模型主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aml_model")
public class AmlModel extends BaseEntity {

    private String modelCode;
    private String modelName;
    private String modelType;
    private String scenario;
    private String algorithmType;
    private String version;
    private String lifecycleStatus;
    private String owner;
    private String governanceLevel;
    private String riskLevel;
    private String trainingDataset;
    private String validationDataset;
    private String testResult;
    private LocalDateTime lastTestTime;
    private String deploymentEnv;
    private LocalDateTime deployedTime;
    private String monitorStatus;
    private BigDecimal precisionRate;
    private BigDecimal recallRate;
    private BigDecimal falsePositiveRate;
    private BigDecimal driftScore;
    private LocalDateTime lastMonitorTime;
    private String iterationPlan;
    private String archiveReason;
    private LocalDateTime archivedTime;
    private String description;
    private String configJson;
}
