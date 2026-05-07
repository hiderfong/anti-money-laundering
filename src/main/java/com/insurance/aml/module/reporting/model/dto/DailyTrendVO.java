package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 每日趋势数据VO
 */
@Data
@Schema(description = "每日趋势视图对象")
public class DailyTrendVO {

    @Schema(description = "日期，格式yyyy-MM-dd")
    private String date;

    @Schema(description = "数量")
    private long count;

    @Schema(description = "金额")
    private BigDecimal amount;
}
