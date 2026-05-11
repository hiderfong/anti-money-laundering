package com.insurance.aml.module.monitoring.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.monitoring.model.dto.MultiLayerTransferResult;
import com.insurance.aml.module.monitoring.model.dto.NetworkDensityResult;
import com.insurance.aml.module.monitoring.model.dto.RingTransactionResult;
import com.insurance.aml.module.monitoring.model.dto.SharedAccountResult;
import com.insurance.aml.module.monitoring.service.GraphAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图分析控制器
 * 提供交易网络关联分析接口
 *
 * 基于Neo4j图数据库实现以下分析场景：
 * 1. 环形交易检测: A->B->C->A，检测资金回流
 * 2. 多层转账追踪: 资金流向深度>=3，追踪资金链
 * 3. 共同账户检测: 多个客户关联同一账户，识别团伙
 * 4. 异常网络密度: 某客户交易关联方过多，识别异常活跃账户
 *
 * @author AML Team
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/graph")
@RequiredArgsConstructor
@Tag(name = "图分析", description = "交易网络关联分析（基于Neo4j）")
@ConditionalOnProperty(name = "aml.neo4j.enabled", havingValue = "true", matchIfMissing = true)
public class GraphController {

    private final GraphAnalysisService graphAnalysisService;

    /**
     * 环形交易检测
     * 从指定客户出发，检测是否存在环形交易路径（A->B->C->A）
     * 用于发现资金回流型洗钱行为
     */
    @GetMapping("/ring-detection")
    @Operation(summary = "环形交易检测", description = "检测指定客户是否存在环形交易路径，识别资金回流")
    public Result<RingTransactionResult> detectRingTransactions(
            @Parameter(description = "客户ID", required = true)
            @RequestParam Long customerId) {

        log.info("环形交易检测请求: customerId={}", customerId);
        RingTransactionResult result = graphAnalysisService.detectRingTransactions(customerId);
        return Result.success(result);
    }

    /**
     * 多层转账追踪
     * 追踪从指定客户出发的资金流向，检测多层转账链
     * 用于发现通过多层转账掩盖资金来源的行为
     */
    @GetMapping("/multi-layer-transfer")
    @Operation(summary = "多层转账追踪", description = "追踪资金流向链，检测深度>=3的多层转账")
    public Result<MultiLayerTransferResult> traceMultiLayerTransfer(
            @Parameter(description = "客户ID", required = true)
            @RequestParam Long customerId,
            @Parameter(description = "最大追踪深度（默认3）")
            @RequestParam(defaultValue = "3") int maxDepth) {

        log.info("多层转账追踪请求: customerId={}, maxDepth={}", customerId, maxDepth);
        MultiLayerTransferResult result = graphAnalysisService.traceMultiLayerTransfer(customerId, maxDepth);
        return Result.success(result);
    }

    /**
     * 共同账户检测
     * 检测指定客户是否与其他客户共享账户
     * 用于发现团伙洗钱中的账户共用行为
     */
    @GetMapping("/shared-accounts")
    @Operation(summary = "共同账户检测", description = "检测多个客户是否关联同一账户，识别团伙洗钱")
    public Result<SharedAccountResult> detectSharedAccounts(
            @Parameter(description = "客户ID", required = true)
            @RequestParam Long customerId) {

        log.info("共同账户检测请求: customerId={}", customerId);
        SharedAccountResult result = graphAnalysisService.detectSharedAccounts(customerId);
        return Result.success(result);
    }

    /**
     * 异常网络密度检测
     * 分析客户交易网络中的关联方数量
     * 用于发现异常活跃的交易网络节点
     */
    @GetMapping("/network-density")
    @Operation(summary = "异常网络密度检测", description = "分析客户交易网络关联方数量，识别异常活跃账户")
    public Result<NetworkDensityResult> analyzeNetworkDensity(
            @Parameter(description = "客户ID", required = true)
            @RequestParam Long customerId,
            @Parameter(description = "关联方数量阈值（默认10）")
            @RequestParam(defaultValue = "10") int densityThreshold) {

        log.info("异常网络密度检测请求: customerId={}, threshold={}", customerId, densityThreshold);
        NetworkDensityResult result = graphAnalysisService.analyzeNetworkDensity(customerId, densityThreshold);
        return Result.success(result);
    }
}
