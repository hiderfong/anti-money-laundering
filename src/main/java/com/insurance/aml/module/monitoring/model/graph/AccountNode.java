package com.insurance.aml.module.monitoring.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * 账户图节点
 * 表示银行账户实体，用于追踪资金流向
 */
@Node("Account")
public class AccountNode {

    @Id
    @GeneratedValue
    private Long neo4jId;

    /**
     * 账号
     */
    private String accountNo;

    /**
     * 开户行
     */
    private String bank;

    public AccountNode() {
    }

    public AccountNode(String accountNo, String bank) {
        this.accountNo = accountNo;
        this.bank = bank;
    }

    public Long getNeo4jId() {
        return neo4jId;
    }

    public void setNeo4jId(Long neo4jId) {
        this.neo4jId = neo4jId;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getBank() {
        return bank;
    }

    public void setBank(String bank) {
        this.bank = bank;
    }
}
