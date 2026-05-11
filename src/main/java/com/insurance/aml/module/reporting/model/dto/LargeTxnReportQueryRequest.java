package com.insurance.aml.module.reporting.model.dto;

import com.insurance.aml.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 大额交易报告分页查询请求DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "大额交易报告查询请求")
public class LargeTxnReportQueryRequest extends PageQuery {

    /**
     * 报告状态
     */
    @Schema(description = "报告状态")
    private String reportStatus;

    /**
     * 客户ID
     */
    @Schema(description = "客户ID")
    private Long customerId;

    /**
     * 开始日期
     */
    @Schema(description = "开始日期")
    private LocalDate startDate;

    /**
     * 结束日期
     */
    @Schema(description = "结束日期")
    private LocalDate endDate;
}
