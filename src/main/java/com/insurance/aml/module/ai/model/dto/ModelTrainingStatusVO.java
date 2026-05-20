package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运维端通用的模型训练状态视图，由 ModelTrainingOpsService 聚合两个模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingStatusVO {

    /** "supervised" 或 "anomaly" */
    private String modelKey;
    private String modelName;
    private String modelVersion;
    /** TRAINED / SKIPPED_* / FAILED / NOT_TRAINED */
    private String status;
    private boolean modelReady;
    private int sampleCount;
    private LocalDateTime trainedAt;
    private String message;
}
