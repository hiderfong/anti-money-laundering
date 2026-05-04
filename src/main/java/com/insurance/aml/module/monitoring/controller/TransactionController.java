package com.insurance.aml.module.monitoring.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.monitoring.model.dto.TransactionIngestRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionQueryRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;
import com.insurance.aml.module.monitoring.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * 交易监测控制器
 * 提供交易录入、查询、日汇总等接口
 *
 * 异步处理说明：
 * - 交易录入使用异步管道：入库后日汇总更新与Kafka发送并行执行
 * - Controller层通过CompletableFuture获取结果，Spring自动处理异步响应
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/transactions")
@RequiredArgsConstructor
@Tag(name = "交易监测")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * 录入交易（异步管道版本）
     *
     * 使用CompletableFuture异步管道：
     * 1. 同步入库获取交易ID
     * 2. 日汇总更新 + Kafka事件发送 并行执行
     * 3. 通过Future返回结果，降低接口响应延迟
     */
    @PostMapping("/ingest")
    @Operation(summary = "录入交易（异步管道）")
    public CompletableFuture<Result<TransactionVO>> ingestTransaction(@Valid @RequestBody TransactionIngestRequest request) {
        log.info("接收到交易录入请求: transactionNo={}, customerId={}, amount={}",
                request.getTransactionNo(), request.getCustomerId(), request.getAmount());

        return transactionService.ingestTransactionAsync(request)
                .thenApply(transaction -> {
                    TransactionVO vo = new TransactionVO();
                    BeanUtils.copyProperties(transaction, vo);
                    log.info("交易录入响应返回: transactionNo={}", vo.getTransactionNo());
                    return Result.success(vo);
                })
                .exceptionally(ex -> {
                    log.error("交易录入失败: transactionNo={}, error={}",
                            request.getTransactionNo(), ex.getMessage(), ex);
                    // 入库异常应返回错误（但如果是Kafka/汇总异常，主流程仍成功）
                    return Result.fail("交易录入失败: " + ex.getMessage());
                });
    }

    /**
     * 分页查询交易
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询交易")
    public Result<PageResult<TransactionVO>> pageQueryTransactions(@Valid TransactionQueryRequest request) {
        PageResult<TransactionVO> pageResult = transactionService.pageQueryTransactions(request);
        return Result.success(pageResult);
    }

    /**
     * 查询交易日汇总
     */
    @GetMapping("/daily-summary")
    @Operation(summary = "查询交易日汇总")
    public Result<TransactionDailySummary> getDailySummary(
            @Parameter(description = "客户ID", required = true) @RequestParam Long customerId,
            @Parameter(description = "汇总日期", required = true)
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @Parameter(description = "交易类型", required = true) @RequestParam String transactionType) {
        TransactionDailySummary summary = transactionService.getDailySummary(customerId, date, transactionType);
        return Result.success(summary);
    }
}
