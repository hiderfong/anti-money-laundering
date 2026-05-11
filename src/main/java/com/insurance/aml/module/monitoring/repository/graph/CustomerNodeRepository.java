package com.insurance.aml.module.monitoring.repository.graph;

import com.insurance.aml.module.monitoring.model.graph.CustomerNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 客户图节点Repository
 * 提供客户相关的图查询能力
 */
@Repository
public interface CustomerNodeRepository extends Neo4jRepository<CustomerNode, Long> {

    /**
     * 根据业务客户ID查找
     */
    CustomerNode findByCustomerId(Long customerId);

    /**
     * 检测环形交易：从指定客户出发，查找经过指定深度后回到自身的路径
     * Cypher查询：MATCH (c:Customer)-[:OWNS]->(a1:Account)-[:SENDS]->(t:Transaction)-[:TO]->(a2:Account)<-[:OWNS]-(c2:Customer)
     *           递归追踪，检测是否存在 c -> ... -> c 的环形路径
     *
     * @param customerId 起始客户ID
     * @param maxDepth   最大追踪深度
     * @return 环形路径列表
     */
    @Query("""
            MATCH path = (start:Customer {customerId: $customerId})-[:OWNS]->(a1:Account)
                         -[:SENDS]->(t:Transaction)-[:TO]->(a2:Account)
                         <-[:OWNS]-(c2:Customer)
                         -[:OWNS*1..3]->(a3:Account)-[:SENDS]->(t2:Transaction)-[:TO]->(a4:Account)
                         <-[:OWNS]-(start)
            RETURN [n IN nodes(path) | CASE
                WHEN n:Customer THEN {type: 'Customer', id: n.customerId, name: n.name}
                WHEN n:Account  THEN {type: 'Account', accountNo: n.accountNo, bank: n.bank}
                WHEN n:Transaction THEN {type: 'Transaction', id: n.transactionId, amount: n.amount}
            END] AS pathNodes
            LIMIT 50
            """)
    List<Map<String, Object>> detectRingTransactions(@Param("customerId") Long customerId);

    /**
     * 检测共同账户：查找与指定客户共享账户的其他客户
     *
     * @param customerId 客户ID
     * @return 共享账户的客户列表及账户信息
     */
    @Query("""
            MATCH (c1:Customer {customerId: $customerId})-[:OWNS]->(a:Account)<-[:OWNS]-(c2:Customer)
            WHERE c1 <> c2
            RETURN c2.customerId AS relatedCustomerId,
                   c2.name AS relatedCustomerName,
                   a.accountNo AS sharedAccountNo,
                   a.bank AS bank
            """)
    List<Map<String, Object>> detectSharedAccounts(@Param("customerId") Long customerId);

    /**
     * 分析客户交易网络密度：统计客户关联的不同交易对手数量
     *
     * @param customerId 客户ID
     * @return 关联方数量统计
     */
    @Query("""
            MATCH (c:Customer {customerId: $customerId})-[:OWNS]->(a:Account)-[:SENDS]->(t:Transaction)-[:TO]->(target:Account)<-[:OWNS]-(c2:Customer)
            RETURN c.customerId AS customerId,
                   c.name AS customerName,
                   COUNT(DISTINCT c2) AS relatedCustomerCount,
                   COUNT(DISTINCT t) AS transactionCount,
                   SUM(t.amount) AS totalAmount
            """)
    Map<String, Object> analyzeNetworkDensity(@Param("customerId") Long customerId);

    /**
     * 查找客户的直接交易对手列表
     *
     * @param customerId 客户ID
     * @return 直接交易对手列表
     */
    @Query("""
            MATCH (c:Customer {customerId: $customerId})-[:OWNS]->(a:Account)-[:SENDS]->(t:Transaction)-[:TO]->(target:Account)<-[:OWNS]-(c2:Customer)
            RETURN DISTINCT c2.customerId AS counterpartyId,
                   c2.name AS counterpartyName,
                   COUNT(t) AS transactionCount,
                   SUM(t.amount) AS totalAmount
            ORDER BY totalAmount DESC
            """)
    List<Map<String, Object>> findDirectCounterparties(@Param("customerId") Long customerId);
}
