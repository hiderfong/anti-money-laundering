package com.insurance.aml.module.monitoring.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 异常网络密度检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkDensityResult {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 关联客户数量
     */
    private Long relatedCustomerCount;

    /**
     * 交易笔数
     */
    private Long transactionCount;

    /**
     * 交易总金额
     */
    private BigDecimal totalAmount;

    /**
     * 是否触发密度异常告警
     */
    private boolean densityAlert;

    /**
     * 直接交易对手列表
     */
    private List<Counterparty> counterparties;

    /**
     * 交易对手详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Counterparty {
        /**
         * 对手客户ID
         */
        private Long counterpartyId;

        /**
         * 对手客户名称
         */
        private String counterpartyName;

        /**
         * 交易笔数
         */
        private Long transactionCount;

        /**
         * 交易总金额
         */
        private BigDecimal totalAmount;
    }
}
