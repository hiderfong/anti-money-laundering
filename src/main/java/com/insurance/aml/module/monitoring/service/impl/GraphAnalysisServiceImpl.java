package com.insurance.aml.module.monitoring.service.impl;

import com.insurance.aml.module.monitoring.model.dto.MultiLayerTransferResult;
import com.insurance.aml.module.monitoring.model.dto.NetworkDensityResult;
import com.insurance.aml.module.monitoring.model.dto.RingTransactionResult;
import com.insurance.aml.module.monitoring.model.dto.SharedAccountResult;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.graph.AccountNode;
import com.insurance.aml.module.monitoring.model.graph.CustomerNode;
import com.insurance.aml.module.monitoring.model.graph.TransactionNode;
import com.insurance.aml.module.monitoring.repository.graph.CustomerNodeRepository;
import com.insurance.aml.module.monitoring.service.GraphAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图分析服务实现类
 * 基于Neo4j图数据库实现交易网络关联分析
 *
 * 图模型:
 *   (:Customer {customerId, name})
 *   (:Account {accountNo, bank})
 *   (:Transaction {transactionId, amount, type, time})
 *   (:Customer)-[:OWNS]->(:Account)
 *   (:Account)-[:SENDS]->(:Transaction)
 *   (:Transaction)-[:TO]->(:Account)
 *
 * @author AML Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aml.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class GraphAnalysisServiceImpl implements GraphAnalysisService {

    private final CustomerNodeRepository customerNodeRepository;
    private final Neo4jClient neo4jClient;

    /**
     * 关联方数量阈值，超过此值触发密度异常告警
     */
    private static final int DEFAULT_DENSITY_THRESHOLD = 10;

    /**
     * 同步交易数据到Neo4j图数据库
     * 创建/更新 客户-账户-交易 节点和关系
     *
     * 图操作流程：
     * 1. MERGE 客户节点（按customerId去重）
     * 2. MERGE 账户节点（按accountNo去重）
     * 3. MERGE 交易节点（按transactionId去重）
     * 4. 创建 OWNS、SENDS、TO 关系
     */
    @Override
    @Transactional
    public void syncTransactionToGraph(Transaction transaction) {
        log.info("[图分析] 同步交易到Neo4j: transactionId={}, customerId={}, amount={}",
                transaction.getId(), transaction.getCustomerId(), transaction.getAmount());

        try {
            Long customerId = transaction.getCustomerId();
            String counterpartyAccount = transaction.getCounterpartyAccount();
            String counterpartyBank = transaction.getCounterpartyBank();
            String counterpartyName = transaction.getCounterpartyName();

            // 1. MERGE 客户节点
            neo4jClient.query("""
                    MERGE (c:Customer {customerId: $customerId})
                    ON CREATE SET c.name = $customerName
                    """)
                    .bind(customerId).to("customerId")
                    .bind(getCustomerName(transaction)).to("customerName")
                    .run();

            // 2. MERGE 交易节点
            neo4jClient.query("""
                    MERGE (t:Transaction {transactionId: $transactionId})
                    ON CREATE SET t.amount = $amount,
                                  t.type = $type,
                                  t.time = datetime($time)
                    """)
                    .bind(transaction.getId()).to("transactionId")
                    .bind(transaction.getAmount()).to("amount")
                    .bind(transaction.getTransactionType()).to("type")
                    .bind(transaction.getTransactionTime() != null ?
                            transaction.getTransactionTime().toString() : null).to("time")
                    .run();

            // 3. 创建客户自有账户节点和OWNS关系
            String customerAccountNo = "CUST_" + customerId;
            neo4jClient.query("""
                    MERGE (a:Account {accountNo: $accountNo})
                    ON CREATE SET a.bank = $bank
                    WITH a
                    MATCH (c:Customer {customerId: $customerId})
                    MERGE (c)-[:OWNS]->(a)
                    """)
                    .bind(customerAccountNo).to("accountNo")
                    .bind("OWN").to("bank")
                    .bind(customerId).to("customerId")
                    .run();

            // 4. 创建 SENDS 关系: 客户账户 -> 交易
            neo4jClient.query("""
                    MATCH (a:Account {accountNo: $accountNo})
                    MATCH (t:Transaction {transactionId: $transactionId})
                    MERGE (a)-[:SENDS]->(t)
                    """)
                    .bind(customerAccountNo).to("accountNo")
                    .bind(transaction.getId()).to("transactionId")
                    .run();

            // 5. 创建对手方账户节点、OWNS关系和TO关系
            if (StringUtils.hasText(counterpartyAccount)) {
                // MERGE 对手方账户
                neo4jClient.query("""
                        MERGE (a:Account {accountNo: $accountNo})
                        ON CREATE SET a.bank = $bank
                        """)
                        .bind(counterpartyAccount).to("accountNo")
                        .bind(counterpartyBank).to("bank")
                        .run();

                // 创建 TO 关系: 交易 -> 对手方账户
                neo4jClient.query("""
                        MATCH (t:Transaction {transactionId: $transactionId})
                        MATCH (a:Account {accountNo: $accountNo})
                        MERGE (t)-[:TO]->(a)
                        """)
                        .bind(transaction.getId()).to("transactionId")
                        .bind(counterpartyAccount).to("accountNo")
                        .run();

                // 如果对手方名称已知，创建对手方客户节点和OWNS关系
                if (StringUtils.hasText(counterpartyName)) {
                    long counterpartyId = Math.abs((long) counterpartyName.hashCode());
                    neo4jClient.query("""
                            MERGE (c:Customer {customerId: $customerId})
                            ON CREATE SET c.name = $name
                            WITH c
                            MATCH (a:Account {accountNo: $accountNo})
                            MERGE (c)-[:OWNS]->(a)
                            """)
                            .bind(counterpartyId).to("customerId")
                            .bind(counterpartyName).to("name")
                            .bind(counterpartyAccount).to("accountNo")
                            .run();
                }
            }

            log.info("[图分析] 交易同步到Neo4j完成: transactionId={}", transaction.getId());

        } catch (Exception e) {
            log.error("[图分析] 同步交易到Neo4j失败: transactionId={}, error={}",
                    transaction.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 环形交易检测
     * 从指定客户出发，检测是否存在环形路径：A -> B -> C -> A
     */
    @Override
    public RingTransactionResult detectRingTransactions(Long customerId) {
        log.info("[图分析] 环形交易检测: customerId={}", customerId);

        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                    MATCH (start:Customer {customerId: $customerId})
                    MATCH path = (start)-[:OWNS*1..2]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)
                                 <-[:OWNS]-(:Customer)-[:OWNS*1..2]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)
                                 <-[:OWNS]-(start)
                    RETURN [n IN nodes(path) |
                        CASE
                            WHEN n:Customer THEN {type: 'Customer', id: toString(n.customerId), name: n.name}
                            WHEN n:Account  THEN {type: 'Account', id: n.accountNo, name: COALESCE(n.bank, ''), bank: n.bank}
                            WHEN n:Transaction THEN {type: 'Transaction', id: toString(n.transactionId), name: toString(n.amount), amount: toString(n.amount)}
                        END
                    ] AS pathNodes
                    LIMIT 50
                    """)
                    .bind(customerId).to("customerId")
                    .fetch().all();

            List<Map<String, Object>> resultList = new ArrayList<>(results);
            List<RingTransactionResult.PathNode> pathNodes = new ArrayList<>();

            if (!resultList.isEmpty()) {
                Map<String, Object> firstResult = resultList.get(0);
                Object pathNodesObj = firstResult.get("pathNodes");

                if (pathNodesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> nodes = (List<Map<String, Object>>) pathNodesObj;
                    for (Map<String, Object> node : nodes) {
                        RingTransactionResult.PathNode pathNode = RingTransactionResult.PathNode.builder()
                                .type((String) node.get("type"))
                                .id((String) node.get("id"))
                                .name((String) node.get("name"))
                                .bank((String) node.get("bank"))
                                .build();
                        if ("Transaction".equals(node.get("type")) && node.get("amount") != null) {
                            try {
                                pathNode.setAmount(new BigDecimal((String) node.get("amount")));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        pathNodes.add(pathNode);
                    }
                }

                log.info("[图分析] 检测到环形交易: customerId={}, 路径节点数={}", customerId, pathNodes.size());
                return RingTransactionResult.builder()
                        .detected(true)
                        .pathNodes(pathNodes)
                        .build();
            }

            log.info("[图分析] 未检测到环形交易: customerId={}", customerId);
            return RingTransactionResult.builder()
                    .detected(false)
                    .pathNodes(Collections.emptyList())
                    .build();

        } catch (Exception e) {
            log.error("[图分析] 环形交易检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return RingTransactionResult.builder()
                    .detected(false)
                    .pathNodes(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 多层转账追踪
     * 追踪从指定客户出发的资金流向链，检测多层转账行为
     */
    @Override
    public MultiLayerTransferResult traceMultiLayerTransfer(Long customerId, int maxDepth) {
        if (maxDepth <= 0) {
            maxDepth = 3;
        }
        log.info("[图分析] 多层转账追踪: customerId={}, maxDepth={}", customerId, maxDepth);

        try {
            String startCustomerName = getCustomerNameById(customerId);

            Collection<Map<String, Object>> chainResults = neo4jClient.query("""
                    MATCH (start:Customer {customerId: $customerId})
                    MATCH path = (start)-[:OWNS]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)
                                 <-[:OWNS]-(:Customer)-[:OWNS]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)
                                 <-[:OWNS]-(:Customer)
                    WITH path, length(path) AS depth
                    WHERE depth >= 5 AND depth <= $maxPathLength
                    RETURN
                        [n IN nodes(path) WHERE n:Customer | n.name][0] AS fromName,
                        [n IN nodes(path) WHERE n:Customer | n.customerId][0] AS fromId,
                        [n IN nodes(path) WHERE n:Customer | n.name][-1] AS toName,
                        [n IN nodes(path) WHERE n:Customer | n.customerId][-1] AS toId,
                        [n IN nodes(path) WHERE n:Transaction | n.amount][0] AS amount,
                        [n IN nodes(path) WHERE n:Account | n.accountNo][1] AS viaAccount,
                        depth
                    ORDER BY depth DESC
                    LIMIT 100
                    """)
                    .bind(customerId).to("customerId")
                    .bind(maxDepth * 2 + 1).to("maxPathLength")
                    .fetch().all();

            List<MultiLayerTransferResult.TransferChain> transferChains = new ArrayList<>();
            int actualMaxDepth = 0;

            for (Map<String, Object> chain : chainResults) {
                int depth = chain.get("depth") != null ? ((Number) chain.get("depth")).intValue() : 0;
                actualMaxDepth = Math.max(actualMaxDepth, depth);

                MultiLayerTransferResult.TransferChain tc = MultiLayerTransferResult.TransferChain.builder()
                        .depth(depth / 2)
                        .fromCustomerId(chain.get("fromId") != null ?
                                ((Number) chain.get("fromId")).longValue() : null)
                        .fromCustomerName((String) chain.get("fromName"))
                        .toCustomerId(chain.get("toId") != null ?
                                ((Number) chain.get("toId")).longValue() : null)
                        .toCustomerName((String) chain.get("toName"))
                        .viaAccount((String) chain.get("viaAccount"))
                        .build();

                if (chain.get("amount") != null) {
                    try {
                        tc.setAmount(new BigDecimal(chain.get("amount").toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }

                transferChains.add(tc);
            }

            boolean suspicious = transferChains.stream()
                    .anyMatch(tc -> tc.getDepth() >= 3);

            log.info("[图分析] 多层转账追踪完成: customerId={}, 链路数={}, 最大深度={}, 可疑={}",
                    customerId, transferChains.size(), actualMaxDepth / 2, suspicious);

            return MultiLayerTransferResult.builder()
                    .startCustomerId(customerId)
                    .startCustomerName(startCustomerName)
                    .chains(transferChains)
                    .maxDepth(actualMaxDepth / 2)
                    .suspicious(suspicious)
                    .build();

        } catch (Exception e) {
            log.error("[图分析] 多层转账追踪异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return MultiLayerTransferResult.builder()
                    .startCustomerId(customerId)
                    .startCustomerName("")
                    .chains(Collections.emptyList())
                    .maxDepth(0)
                    .suspicious(false)
                    .build();
        }
    }

    /**
     * 共同账户检测
     * 检测指定客户是否与其他客户共享账户
     */
    @Override
    public SharedAccountResult detectSharedAccounts(Long customerId) {
        log.info("[图分析] 共同账户检测: customerId={}", customerId);

        try {
            Collection<Map<String, Object>> results = neo4jClient.query("""
                    MATCH (c1:Customer {customerId: $customerId})-[:OWNS]->(a:Account)<-[:OWNS]-(c2:Customer)
                    WHERE c1 <> c2
                    RETURN a.accountNo AS accountNo,
                           a.bank AS bank,
                           c2.customerId AS relatedCustomerId,
                           c2.name AS relatedCustomerName
                    """)
                    .bind(customerId).to("customerId")
                    .fetch().all();

            List<SharedAccountResult.SharedAccount> sharedAccounts = new ArrayList<>();

            for (Map<String, Object> row : results) {
                SharedAccountResult.SharedAccount sa = SharedAccountResult.SharedAccount.builder()
                        .accountNo((String) row.get("accountNo"))
                        .bank((String) row.get("bank"))
                        .relatedCustomerId(row.get("relatedCustomerId") != null ?
                                ((Number) row.get("relatedCustomerId")).longValue() : null)
                        .relatedCustomerName((String) row.get("relatedCustomerName"))
                        .build();
                sharedAccounts.add(sa);
            }

            boolean detected = !sharedAccounts.isEmpty();

            log.info("[图分析] 共同账户检测完成: customerId={}, 共享账户数={}", customerId, sharedAccounts.size());

            return SharedAccountResult.builder()
                    .customerId(customerId)
                    .detected(detected)
                    .sharedAccounts(sharedAccounts)
                    .build();

        } catch (Exception e) {
            log.error("[图分析] 共同账户检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return SharedAccountResult.builder()
                    .customerId(customerId)
                    .detected(false)
                    .sharedAccounts(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 异常网络密度检测
     * 分析客户交易网络中关联方数量，判断是否存在异常活跃
     */
    @Override
    public NetworkDensityResult analyzeNetworkDensity(Long customerId, int densityThreshold) {
        if (densityThreshold <= 0) {
            densityThreshold = DEFAULT_DENSITY_THRESHOLD;
        }
        log.info("[图分析] 异常网络密度检测: customerId={}, threshold={}", customerId, densityThreshold);

        try {
            Optional<Map<String, Object>> densityOpt = neo4jClient.query("""
                    MATCH (c:Customer {customerId: $customerId})-[:OWNS]->(:Account)-[:SENDS]->(:Transaction)-[:TO]->(:Account)<-[:OWNS]-(c2:Customer)
                    RETURN c.customerId AS customerId,
                           c.name AS customerName,
                           COUNT(DISTINCT c2) AS relatedCustomerCount,
                           COUNT(DISTINCT c2) AS transactionCount,
                           SUM(0) AS totalAmount
                    """)
                    .bind(customerId).to("customerId")
                    .fetch().one();

            Long relatedCount = 0L;
            Long txnCount = 0L;
            BigDecimal totalAmount = BigDecimal.ZERO;
            String customerName = "";

            if (densityOpt.isPresent()) {
                Map<String, Object> density = densityOpt.get();
                customerName = (String) density.get("customerName");
                relatedCount = density.get("relatedCustomerCount") != null ?
                        ((Number) density.get("relatedCustomerCount")).longValue() : 0L;
                txnCount = density.get("transactionCount") != null ?
                        ((Number) density.get("transactionCount")).longValue() : 0L;
                totalAmount = density.get("totalAmount") != null ?
                        new BigDecimal(density.get("totalAmount").toString()) : BigDecimal.ZERO;
            }

            Collection<Map<String, Object>> counterpartiesData = neo4jClient.query("""
                    MATCH (c:Customer {customerId: $customerId})-[:OWNS]->(:Account)-[:SENDS]->(t:Transaction)-[:TO]->(:Account)<-[:OWNS]-(c2:Customer)
                    RETURN c2.customerId AS counterpartyId,
                           c2.name AS counterpartyName,
                           COUNT(t) AS transactionCount,
                           SUM(t.amount) AS totalAmount
                    ORDER BY totalAmount DESC
                    LIMIT 50
                    """)
                    .bind(customerId).to("customerId")
                    .fetch().all();

            List<NetworkDensityResult.Counterparty> counterparties = new ArrayList<>();
            for (Map<String, Object> row : counterpartiesData) {
                NetworkDensityResult.Counterparty cp = NetworkDensityResult.Counterparty.builder()
                        .counterpartyId(row.get("counterpartyId") != null ?
                                ((Number) row.get("counterpartyId")).longValue() : null)
                        .counterpartyName((String) row.get("counterpartyName"))
                        .transactionCount(row.get("transactionCount") != null ?
                                ((Number) row.get("transactionCount")).longValue() : 0L)
                        .totalAmount(row.get("totalAmount") != null ?
                                new BigDecimal(row.get("totalAmount").toString()) : BigDecimal.ZERO)
                        .build();
                counterparties.add(cp);
            }

            boolean densityAlert = relatedCount >= densityThreshold;

            log.info("[图分析] 网络密度检测完成: customerId={}, 关联客户数={}, 告警={}",
                    customerId, relatedCount, densityAlert);

            return NetworkDensityResult.builder()
                    .customerId(customerId)
                    .customerName(customerName)
                    .relatedCustomerCount(relatedCount)
                    .transactionCount(txnCount)
                    .totalAmount(totalAmount)
                    .densityAlert(densityAlert)
                    .counterparties(counterparties)
                    .build();

        } catch (Exception e) {
            log.error("[图分析] 网络密度检测异常: customerId={}, error={}", customerId, e.getMessage(), e);
            return NetworkDensityResult.builder()
                    .customerId(customerId)
                    .customerName("")
                    .relatedCustomerCount(0L)
                    .transactionCount(0L)
                    .totalAmount(BigDecimal.ZERO)
                    .densityAlert(false)
                    .counterparties(Collections.emptyList())
                    .build();
        }
    }

    // ==================== 私有辅助方法 ====================

    private String getCustomerName(Transaction transaction) {
        return "Customer_" + transaction.getCustomerId();
    }

    private String getCustomerNameById(Long customerId) {
        try {
            Optional<Map<String, Object>> result = neo4jClient.query("""
                    MATCH (c:Customer {customerId: $customerId})
                    RETURN c.name AS name
                    """)
                    .bind(customerId).to("customerId")
                    .fetch().one();
            return result.map(r -> (String) r.get("name")).orElse("Customer_" + customerId);
        } catch (Exception e) {
            return "Customer_" + customerId;
        }
    }
}
