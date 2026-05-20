package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 无监督异常检测训练/状态结果。
 * status: TRAINED / SKIPPED_INSUFFICIENT / SKIPPED_IN_PROGRESS / FAILED / NOT_TRAINED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyTrainingResultVO {

    private String status;
    private boolean modelReady;
    private int sampleCount;
    private long trainDurationMs;
    private LocalDateTime trainedAt;
    private String message;
}
