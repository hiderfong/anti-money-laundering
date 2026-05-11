package com.insurance.aml.module.monitoring.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户图节点
 * 表示AML系统中的客户实体，用于交易网络关联分析
 */
@Node("Customer")
public class CustomerNode {

    @Id
    @GeneratedValue
    private Long neo4jId;

    /**
     * 业务ID（对应MySQL中客户ID）
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String name;

    /**
     * 客户关联的账户
     */
    @Relationship(type = "OWNS", direction = Relationship.Direction.OUTGOING)
    private Set<AccountNode> accounts = new HashSet<>();

    public CustomerNode() {
    }

    public CustomerNode(Long customerId, String name) {
        this.customerId = customerId;
        this.name = name;
    }

    public Long getNeo4jId() {
        return neo4jId;
    }

    public void setNeo4jId(Long neo4jId) {
        this.neo4jId = neo4jId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<AccountNode> getAccounts() {
        return accounts;
    }

    public void setAccounts(Set<AccountNode> accounts) {
        this.accounts = accounts;
    }
}
