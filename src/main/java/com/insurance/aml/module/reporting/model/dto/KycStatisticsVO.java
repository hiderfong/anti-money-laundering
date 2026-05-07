package com.insurance.aml.module.reporting.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * KYC统计VO
 */
@Data
@Schema(description = "KYC统计视图对象")
public class KycStatisticsVO {

    @Schema(description = "客户总数")
    private long totalCustomers;

    @Schema(description = "KYC已完成数")
    private long kycComplete;

    @Schema(description = "KYC未完成数")
    private long kycIncomplete;

    @Schema(description = "KYC审核中数")
    private long kycReviewing;

    @Schema(description = "风险等级分布")
    private Map<String, Long> riskDistribution;
}
