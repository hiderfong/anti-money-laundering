package com.insurance.aml.module.reporting.model.dto;

import lombok.Data;

/**
 * 告警统计VO
 */
@Data
public class AlertStatisticsVO {

    /** 告警类型 */
    private String alertType;

    /** 数量 */
    private long count;

    /** 占比（百分比） */
    private double percentage;
}
