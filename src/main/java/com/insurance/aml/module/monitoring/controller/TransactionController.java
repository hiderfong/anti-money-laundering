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
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 交易监测控制器
 * 提供交易录入、查询、日汇总等接口
 */
@RestController
@RequestMapping("/monitoring/transactions")
@RequiredArgsConstructor
@Tag(name = "交易监测")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * 录入交易
     */
    @PostMapping("/ingest")
    @Operation(summary = "录入交易")
    public Result<TransactionVO> ingestTransaction(@Valid @RequestBody TransactionIngestRequest request) {
        Transaction transaction = transactionService.ingestTransaction(request);
        TransactionVO vo = new TransactionVO();
        BeanUtils.copyProperties(transaction, vo);
        return Result.success(vo);
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
