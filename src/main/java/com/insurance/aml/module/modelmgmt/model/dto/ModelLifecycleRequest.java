package com.insurance.aml.module.modelmgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型生命周期动作请求。
 */
@Data
@Schema(description = "模型生命周期动作请求")
public class ModelLifecycleRequest {

    private String operator;
    private String actionSummary;
    private String artifactRef;
    private String deploymentEnv;
    private String testDataset;
    private String targetVersion;
    private String iterationPlan;
    private String archiveReason;
    private String monitorStatus;
    private BigDecimal precisionRate;
    private BigDecimal recallRate;
    private BigDecimal falsePositiveRate;
    private BigDecimal driftScore;
}
