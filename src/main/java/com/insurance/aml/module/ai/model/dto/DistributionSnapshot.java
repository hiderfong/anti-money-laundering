package com.insurance.aml.module.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 训练时输出分布快照，作为 PSI 漂移监控的参考基线。持久化到模型旁路 JSON 文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributionSnapshot {

    private int bins;
    private double lo;
    private double hi;
    private int[] counts;
    private int total;
    private LocalDateTime capturedAt;
}
