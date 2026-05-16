package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.module.monitoring.model.dto.MultiLayerTransferResult;
import com.insurance.aml.module.monitoring.model.dto.NetworkDensityResult;
import com.insurance.aml.module.monitoring.model.dto.RingTransactionResult;
import com.insurance.aml.module.monitoring.model.dto.SharedAccountResult;
import com.insurance.aml.module.monitoring.model.entity.Transaction;

/**
 * 交易网络图分析服务接口
 * 使用Neo4j进行交易关联分析
 *
 * 分析场景:
 * 1. 环形交易检测: A→B→C→A 资金回流
 * 2. 多层转账追踪: 资金流向深度>=3
 * 3. 共同账户检测: 多客户关联同一账户
 * 4. 异常网络密度: 交易关联方过多
 */
public interface GraphAnalysisService {

    /**
     * 同步交易数据到Neo4j图数据库
     */
    void syncTransactionToGraph(Transaction transaction);

    /**
     * 批量同步已有交易到Neo4j图数据库。
     *
     * @param limit 最大同步笔数
     * @param sourceSystem 来源系统过滤；为空时同步全部交易
     * @return 成功同步笔数
     */
    long syncTransactionsToGraph(int limit, String sourceSystem);

    /**
     * 检测环形交易
     */
    RingTransactionResult detectRingTransactions(Long customerId);

    /**
     * 多层转账追踪
     */
    MultiLayerTransferResult traceMultiLayerTransfer(Long customerId, int maxDepth);

    /**
     * 检测共同账户
     */
    SharedAccountResult detectSharedAccounts(Long customerId);

    /**
     * 检测异常网络密度
     */
    NetworkDensityResult analyzeNetworkDensity(Long customerId, int densityThreshold);
}
