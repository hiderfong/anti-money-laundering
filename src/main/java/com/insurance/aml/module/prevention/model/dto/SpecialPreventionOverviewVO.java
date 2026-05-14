package com.insurance.aml.module.prevention.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 特别预防中心概览。
 */
@Data
@Builder
@Schema(description = "特别预防中心概览")
public class SpecialPreventionOverviewVO {

    private long activeMeasures;
    private long activeFreezeRecords;
    private long pendingScreeningResults;
    private long activeRetrospectiveJobs;
}
