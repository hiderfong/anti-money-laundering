package com.insurance.aml.module.monitoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.monitoring.mapper.TransactionDailySummaryMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 大额交易监测服务
 * 根据监管要求对大额交易进行识别和标记
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LargeTransactionService {

    private final TransactionMapper transactionMapper;
    private final TransactionDailySummaryMapper dailySummaryMapper;

    /**
     * 大额交易阈值配置
     * key: 交易场景标识
     * value: 阈值金额
     */
    private static final Map<String, BigDecimal> THRESHOLD_CONFIG = new HashMap<>();

    static {
        // 现金交易阈值：5万元
        THRESHOLD_CONFIG.put("CASH", new BigDecimal("50000"));
        // 个人转账阈值：200万元
        THRESHOLD_CONFIG.put("TRANSFER_INDIVIDUAL", new BigDecimal("2000000"));
        // 企业转账阈值：200万元
        THRESHOLD_CONFIG.put("TRANSFER_CORPORATE", new BigDecimal("2000000"));
        // 跨境个人交易阈值：20万元
        THRESHOLD_CONFIG.put("CROSS_BORDER_INDIVIDUAL", new BigDecimal("200000"));
        // 跨境交易阈值：200万元
        THRESHOLD_CONFIG.put("CROSS_BORDER", new BigDecimal("2000000"));
    }

    /**
     * 检查大额交易
     * 1. 检查单笔金额是否超过阈值
     * 2. 检查当日累计金额是否超过阈值
     * 3. 标记大额交易标志
     *
     * @param transaction 待检查的交易
     */
    public void checkLargeTransaction(Transaction transaction) {
        log.info("执行大额交易检查: transactionId={}, amount={}, paymentMethod={}, isCrossBorder={}",
                transaction.getId(), transaction.getAmount(), transaction.getPaymentMethod(), transaction.getIsCrossBorder());

        boolean isLarge = false;
        String matchReason = "";

        // 1. 检查单笔金额阈值
        BigDecimal threshold = getThreshold(transaction.getPaymentMethod(), transaction.getIsCrossBorder());
        if (transaction.getAmount().compareTo(threshold) >= 0) {
            isLarge = true;
            matchReason = "单笔金额 " + transaction.getAmount() + " 超过阈值 " + threshold;
            log.warn("触发大额交易[单笔]: transactionNo={}, amount={}, threshold={}",
                    transaction.getTransactionNo(), transaction.getAmount(), threshold);
        }

        // 2. 检查当日累计金额
        BigDecimal dailyTotal = getDailyCumulativeAmount(transaction.getCustomerId(),
                transaction.getTransactionType(), transaction.getPaymentMethod());
        BigDecimal cumulativeThreshold = getThreshold(transaction.getPaymentMethod(), transaction.getIsCrossBorder());
        if (dailyTotal.compareTo(cumulativeThreshold) >= 0) {
            isLarge = true;
            if (matchReason.isEmpty()) {
                matchReason = "当日累计金额 " + dailyTotal + " 超过阈值 " + cumulativeThreshold;
            }
            log.warn("触发大额交易[累计]: transactionNo={}, dailyTotal={}, threshold={}",
                    transaction.getTransactionNo(), dailyTotal, cumulativeThreshold);
        }

        // 3. 标记日汇总中的大额交易标志
        if (isLarge) {
            markLargeTxnFlag(transaction.getCustomerId(), LocalDate.now(), transaction.getTransactionType());
            log.info("大额交易已标记: transactionNo={}, reason={}", transaction.getTransactionNo(), matchReason);
        }
    }

    /**
     * 简单判断是否为大额交易
     *
     * @param txn 交易记录
     * @return 是否大额交易
     */
    public boolean isLargeTransaction(Transaction txn) {
        BigDecimal threshold = getThreshold(txn.getPaymentMethod(), txn.getIsCrossBorder());
        return txn.getAmount().compareTo(threshold) >= 0;
    }

    /**
     * 根据支付方式和是否跨境获取对应的阈值
     */
    private BigDecimal getThreshold(String paymentMethod, Boolean isCrossBorder) {
        if (Boolean.TRUE.equals(isCrossBorder)) {
            return THRESHOLD_CONFIG.getOrDefault("CROSS_BORDER", new BigDecimal("2000000"));
        }
        if ("CASH".equals(paymentMethod)) {
            return THRESHOLD_CONFIG.getOrDefault("CASH", new BigDecimal("50000"));
        }
        return THRESHOLD_CONFIG.getOrDefault("TRANSFER_INDIVIDUAL", new BigDecimal("2000000"));
    }

    /**
     * 获取当日累计交易金额
     */
    private BigDecimal getDailyCumulativeAmount(Long customerId, String transactionType, String paymentMethod) {
        LambdaQueryWrapper<TransactionDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionDailySummary::getCustomerId, customerId)
                .eq(TransactionDailySummary::getSummaryDate, LocalDate.now())
                .eq(TransactionDailySummary::getTransactionType, transactionType);

        TransactionDailySummary summary = dailySummaryMapper.selectOne(wrapper);
        if (summary != null && summary.getTotalAmount() != null) {
            return summary.getTotalAmount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 标记日汇总记录为大额交易
     */
    private void markLargeTxnFlag(Long customerId, LocalDate date, String transactionType) {
        LambdaQueryWrapper<TransactionDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionDailySummary::getCustomerId, customerId)
                .eq(TransactionDailySummary::getSummaryDate, date)
                .eq(TransactionDailySummary::getTransactionType, transactionType);

        TransactionDailySummary summary = dailySummaryMapper.selectOne(wrapper);
        if (summary != null) {
            summary.setLargeTxnFlag(true);
            summary.setUpdatedTime(LocalDateTime.now());
            dailySummaryMapper.updateById(summary);
        }
    }
}
