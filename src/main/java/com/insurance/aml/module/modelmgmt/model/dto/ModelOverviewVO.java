package com.insurance.aml.module.modelmgmt.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模型管理概览。
 */
@Data
@Builder
@Schema(description = "模型管理概览")
public class ModelOverviewVO {

    private long totalModels;
    private long draftModels;
    private long testingModels;
    private long deployedModels;
    private long monitoringModels;
    private long iterationModels;
    private long archivedModels;
    private long attentionModels;
    private BigDecimal averageFalsePositiveRate;
    private BigDecimal averageDriftScore;
}
