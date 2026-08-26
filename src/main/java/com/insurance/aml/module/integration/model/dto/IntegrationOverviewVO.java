package com.insurance.aml.module.integration.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 集成中心运行概览。
 */
@Data
@Builder
public class IntegrationOverviewVO {
    private long totalConnectors;
    private long healthyConnectors;
    private long unhealthyConnectors;
    private long enabledJobs;
    private long runningJobs;
    private long failedRunsToday;
    private long recordsWrittenToday;
    private double successRateToday;
}
