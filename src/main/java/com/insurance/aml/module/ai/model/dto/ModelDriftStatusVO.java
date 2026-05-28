package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型漂移监控结果。status: NORMAL / WARN / SEVERE / UNAVAILABLE
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDriftStatusVO {

    /** "supervised" 或 "anomaly" */
    private String modelKey;
    private String status;
    private Double psi;
    private int sampleCount;
    private int baselineSampleCount;
    private LocalDateTime computedAt;
    private String message;
}
