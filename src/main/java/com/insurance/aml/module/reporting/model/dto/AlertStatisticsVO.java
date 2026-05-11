package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警统计VO
 */
@Data
@Schema(description = "告警统计视图对象")
public class AlertStatisticsVO {

    @Schema(description = "告警类型")
    private String alertType;

    @Schema(description = "数量")
    private long count;

    @Schema(description = "占比百分比")
    private double percentage;
}
