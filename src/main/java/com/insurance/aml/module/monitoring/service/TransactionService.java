package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.monitoring.model.dto.TransactionIngestRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionQueryRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;

import java.time.LocalDate;

/**
 * 交易服务接口
 */
public interface TransactionService {

    /**
     * 录入交易
     *
     * @param req 交易录入请求
     * @return 保存后的交易记录
     */
    Transaction ingestTransaction(TransactionIngestRequest req);

    /**
     * 分页查询交易
     *
     * @param req 分页查询请求
     * @return 分页结果
     */
    PageResult<TransactionVO> pageQueryTransactions(TransactionQueryRequest req);

    /**
     * 获取交易日汇总
     *
     * @param customerId      客户ID
     * @param date            汇总日期
     * @param transactionType 交易类型
     * @return 日汇总记录
     */
    TransactionDailySummary getDailySummary(Long customerId, LocalDate date, String transactionType);
}
