package com.insurance.aml.module.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI风险评分使用的核心业务特征摘要。
 */
@Data
@Schema(description = "AI风险特征摘要")
public class AiRiskFeatureSummaryVO {

    @Schema(description = "近90天交易笔数")
    private int transactionCount90d;

    @Schema(description = "近90天交易总金额")
    private BigDecimal totalAmount90d = BigDecimal.ZERO;

    @Schema(description = "近90天最大单笔金额")
    private BigDecimal maxAmount90d = BigDecimal.ZERO;

    @Schema(description = "近90天现金交易笔数")
    private int cashTransactionCount90d;

    @Schema(description = "近90天跨境交易笔数")
    private int crossBorderTransactionCount90d;

    @Schema(description = "近90天大额交易笔数")
    private int highAmountTransactionCount90d;

    @Schema(description = "近90天不同交易对手数量")
    private int distinctCounterpartyCount90d;

    @Schema(description = "活跃预警数量")
    private int activeAlertCount;

    @Schema(description = "高风险预警数量")
    private int highRiskAlertCount;

    @Schema(description = "已确认可疑预警数量")
    private int confirmedSuspiciousAlertCount;

    @Schema(description = "案件数量")
    private int caseCount;

    @Schema(description = "STR报告数量")
    private int strReportCount;

    @Schema(description = "名单筛查命中数量")
    private int watchlistHitCount;

    @Schema(description = "受益所有人数量")
    private int beneficialOwnerCount;

    @Schema(description = "高持股/控制比例受益所有人数量")
    private int controllingOwnerCount;

    @Schema(description = "高风险产品数量")
    private int highRiskProductCount;

    @Schema(description = "客户资料完整度百分比")
    private int kycCompleteness;

    @Schema(description = "交易金额相对历史均值倍数")
    private BigDecimal amountToAverageRatio = BigDecimal.ZERO;

    @Schema(description = "同一交易对手账户关联客户数")
    private int sharedCounterpartyAccountCustomerCount;

    @Schema(description = "规则/模型关联预警数量")
    private int relatedAlertCount;
}
