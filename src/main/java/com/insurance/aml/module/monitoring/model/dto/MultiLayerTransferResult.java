package com.insurance.aml.module.monitoring.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多层转账追踪结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiLayerTransferResult {

    /**
     * 起始客户ID
     */
    private Long startCustomerId;

    /**
     * 起始客户名称
     */
    private String startCustomerName;

    /**
     * 追踪到的转账链路
     */
    private List<TransferChain> chains;

    /**
     * 最大追踪深度
     */
    private int maxDepth;

    /**
     * 是否检测到可疑多层转账
     */
    private boolean suspicious;

    /**
     * 转账链路详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferChain {
        /**
         * 链路深度
         */
        private int depth;

        /**
         * 发起方客户ID
         */
        private Long fromCustomerId;

        /**
         * 发起方客户名称
         */
        private String fromCustomerName;

        /**
         * 接收方客户ID
         */
        private Long toCustomerId;

        /**
         * 接收方客户名称
         */
        private String toCustomerName;

        /**
         * 转账金额
         */
        private BigDecimal amount;

        /**
         * 中间经过的账户
         */
        private String viaAccount;
    }
}
