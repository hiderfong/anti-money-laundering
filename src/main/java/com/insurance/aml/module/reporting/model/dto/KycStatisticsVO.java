package com.insurance.aml.module.reporting.model.dto;

import lombok.Data;

import java.util.Map;

/**
 * KYC统计VO
 */
@Data
public class KycStatisticsVO {

    /** 客户总数 */
    private long totalCustomers;

    /** KYC已完成数 */
    private long kycComplete;

    /** KYC未完成数 */
    private long kycIncomplete;

    /** KYC审核中数 */
    private long kycReviewing;

    /** 风险等级分布（LOW/MEDIUM/HIGH → 数量） */
    private Map<String, Long> riskDistribution;
}
