package com.insurance.aml.module.ai.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监督模型训练/状态结果。
 */
@Data
@Builder
public class AiRiskTrainingResultVO {

    /** TRAINED / SKIPPED_INSUFFICIENT / SKIPPED_SINGLE_CLASS / NOT_TRAINED */
    private String status;
    private boolean modelReady;
    private int sampleCount;
    private int positiveCount;
    private int negativeCount;
    private double accuracy;
    private double auc;
    private LocalDateTime trainedAt;
    private String message;
}
