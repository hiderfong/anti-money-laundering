package com.insurance.aml.module.monitoring.service.impl;

import com.insurance.aml.module.monitoring.model.dto.MultiLayerTransferResult;
import com.insurance.aml.module.monitoring.model.dto.NetworkDensityResult;
import com.insurance.aml.module.monitoring.model.dto.RingTransactionResult;
import com.insurance.aml.module.monitoring.model.dto.SharedAccountResult;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.service.GraphAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;

/**
 * Fallback graph analysis service used when Neo4j is disabled.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "aml.neo4j.enabled", havingValue = "false")
public class NoopGraphAnalysisServiceImpl implements GraphAnalysisService {

    @Override
    public void syncTransactionToGraph(Transaction transaction) {
        log.debug("[图分析] Neo4j已关闭，跳过交易图谱同步: transactionNo={}",
                transaction != null ? transaction.getTransactionNo() : null);
    }

    @Override
    public long syncTransactionsToGraph(int limit, String sourceSystem) {
        log.info("[图分析] Neo4j已关闭，跳过历史交易批量同步: limit={}, sourceSystem={}", limit, sourceSystem);
        return 0L;
    }

    @Override
    public RingTransactionResult detectRingTransactions(Long customerId) {
        return RingTransactionResult.builder()
                .detected(false)
                .pathNodes(Collections.emptyList())
                .build();
    }

    @Override
    public MultiLayerTransferResult traceMultiLayerTransfer(Long customerId, int maxDepth) {
        return MultiLayerTransferResult.builder()
                .startCustomerId(customerId)
                .startCustomerName("")
                .chains(Collections.emptyList())
                .maxDepth(0)
                .suspicious(false)
                .build();
    }

    @Override
    public SharedAccountResult detectSharedAccounts(Long customerId) {
        return SharedAccountResult.builder()
                .customerId(customerId)
                .detected(false)
                .sharedAccounts(Collections.emptyList())
                .build();
    }

    @Override
    public NetworkDensityResult analyzeNetworkDensity(Long customerId, int densityThreshold) {
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
