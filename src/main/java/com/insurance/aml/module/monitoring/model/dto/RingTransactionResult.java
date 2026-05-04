package com.insurance.aml.module.monitoring.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 环形交易检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RingTransactionResult {

    /**
     * 是否检测到环形交易
     */
    private boolean detected;

    /**
     * 环形路径节点列表
     */
    private List<PathNode> pathNodes;

    /**
     * 路径节点详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathNode {
        /**
         * 节点类型: Customer, Account, Transaction
         */
        private String type;

        /**
         * 节点标识
         */
        private String id;

        /**
         * 节点名称（客户名称/账号/交易ID）
         */
        private String name;

        /**
         * 金额（仅交易节点）
         */
        private BigDecimal amount;

        /**
         * 银行（仅账户节点）
         */
        private String bank;
    }
}
