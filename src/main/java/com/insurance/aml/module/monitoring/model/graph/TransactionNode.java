package com.insurance.aml.module.monitoring.model.graph;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易图节点
 * 表示交易实体，用于构建交易网络图谱
 */
@Node("Transaction")
public class TransactionNode {

    @Id
    @GeneratedValue
    private Long neo4jId;

    /**
     * 交易ID（对应MySQL中交易ID）
     */
    private Long transactionId;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 交易类型
     */
    private String type;

    /**
     * 交易时间
     */
    private LocalDateTime time;

    public TransactionNode() {
    }

    public TransactionNode(Long transactionId, BigDecimal amount, String type, LocalDateTime time) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.type = type;
        this.time = time;
    }

    public Long getNeo4jId() {
        return neo4jId;
    }

    public void setNeo4jId(Long neo4jId) {
        this.neo4jId = neo4jId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
