package com.insurance.aml.module.monitoring.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.monitoring.model.dto.MultiLayerTransferResult;
import com.insurance.aml.module.monitoring.model.dto.NetworkDensityResult;
import com.insurance.aml.module.monitoring.model.dto.RingTransactionResult;
import com.insurance.aml.module.monitoring.model.dto.SharedAccountResult;
import com.insurance.aml.module.monitoring.service.GraphAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 交易网络图分析控制器
 */
@RestController
@RequestMapping("/monitoring/graph")
@Tag(name = "交易网络图分析")
@RequiredArgsConstructor
public class GraphAnalysisController {

    private final GraphAnalysisService graphAnalysisService;

    @GetMapping("/ring/{customerId}")
    @Operation(summary = "检测环形交易", description = "检测指定客户是否存在A→B→C→A的资金回流")
    public Result<RingTransactionResult> detectRingTransactions(
            @PathVariable Long customerId) {
        return Result.success(graphAnalysisService.detectRingTransactions(customerId));
    }

    @GetMapping("/trace/{customerId}")
    @Operation(summary = "追踪资金流向", description = "从指定客户追踪N层资金去向")
    public Result<MultiLayerTransferResult> traceMultiLayerTransfer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "3") int maxDepth) {
        return Result.success(graphAnalysisService.traceMultiLayerTransfer(customerId, maxDepth));
    }

    @GetMapping("/shared-accounts/{customerId}")
    @Operation(summary = "检测共同账户", description = "检测指定客户是否与其他客户共享账户")
    public Result<SharedAccountResult> detectSharedAccounts(
            @PathVariable Long customerId) {
        return Result.success(graphAnalysisService.detectSharedAccounts(customerId));
    }

    @GetMapping("/density/{customerId}")
    @Operation(summary = "检测异常网络密度", description = "分析客户交易网络中关联方数量")
    public Result<NetworkDensityResult> analyzeNetworkDensity(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "10") int densityThreshold) {
        return Result.success(graphAnalysisService.analyzeNetworkDensity(customerId, densityThreshold));
    }
}
