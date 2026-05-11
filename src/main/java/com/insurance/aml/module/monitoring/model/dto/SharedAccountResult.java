package com.insurance.aml.module.monitoring.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 共同账户检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedAccountResult {

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 是否检测到共同账户
     */
    private boolean detected;

    /**
     * 共享账户列表
     */
    private List<SharedAccount> sharedAccounts;

    /**
     * 共享账户详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedAccount {
        /**
         * 共享的账号
         */
        private String accountNo;

        /**
         * 开户行
         */
        private String bank;

        /**
         * 关联的客户ID
         */
        private Long relatedCustomerId;

        /**
         * 关联的客户名称
         */
        private String relatedCustomerName;
    }
}
