package com.insurance.aml.module.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.monitoring.mapper.TransactionDailySummaryMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.kafka.TransactionEventProducer;
import com.insurance.aml.module.monitoring.model.dto.TransactionEvent;
import com.insurance.aml.module.monitoring.model.dto.TransactionIngestRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionQueryRequest;
import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;
import com.insurance.aml.module.monitoring.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 交易服务实现类
 * 负责交易的录入、查询和日汇总管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionMapper transactionMapper;
    private final TransactionDailySummaryMapper dailySummaryMapper;
    private final IdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final TransactionEventProducer transactionEventProducer;

    /** Redis日汇总Key前缀 */
    private static final String SUMMARY_KEY_PREFIX = "aml:txn:summary:";
    /** Redis Key日期格式 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 交易流水号前缀
     */
    private static final String PREFIX_TRANSACTION = "TXN";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Transaction ingestTransaction(TransactionIngestRequest req) {
        log.info("录入交易: transactionNo={}, customerId={}, amount={}", req.getTransactionNo(), req.getCustomerId(), req.getAmount());

        // 1. 构建交易记录，生成业务流水号
        Transaction transaction = new Transaction();
        BeanUtils.copyProperties(req, transaction);
        if (!StringUtils.hasText(transaction.getTransactionNo())) {
            transaction.setTransactionNo(idGenerator.generate(PREFIX_TRANSACTION));
        }
        if (!StringUtils.hasText(transaction.getCurrency())) {
            transaction.setCurrency("CNY");
        }
        if (transaction.getIsCrossBorder() == null) {
            transaction.setIsCrossBorder(false);
        }
        if (!StringUtils.hasText(transaction.getStatus())) {
            transaction.setStatus("SUCCESS");
        }

        // 2. 保存交易记录
        transactionMapper.insert(transaction);
        log.info("交易保存成功: id={}, transactionNo={}", transaction.getId(), transaction.getTransactionNo());

        // 3. 更新日汇总：Redis实时计数 + DB汇总更新
        updateDailySummary(transaction);

        // 4. 发送交易事件到Kafka，由Consumer异步触发规则引擎评估
        try {
            TransactionEvent event = TransactionEvent.fromEntity(transaction);
            transactionEventProducer.sendTransactionEvent(event);
            log.info("交易事件已发送到Kafka: transactionNo={}", transaction.getTransactionNo());
        } catch (Exception e) {
            log.error("交易事件发送Kafka失败，交易ID={}: {}", transaction.getId(), e.getMessage(), e);
            // Kafka发送失败不影响交易录入流程
        }

        return transaction;
    }

    @Override
    public PageResult<TransactionVO> pageQueryTransactions(TransactionQueryRequest req) {
        log.debug("分页查询交易: page={}, size={}", req.getPage(), req.getSize());

        // 构建查询条件
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<>();

        if (req.getCustomerId() != null) {
            wrapper.eq(Transaction::getCustomerId, req.getCustomerId());
        }
        if (req.getPolicyId() != null) {
            wrapper.eq(Transaction::getPolicyId, req.getPolicyId());
        }
        if (StringUtils.hasText(req.getTransactionType())) {
            wrapper.eq(Transaction::getTransactionType, req.getTransactionType());
        }
        if (StringUtils.hasText(req.getStatus())) {
            wrapper.eq(Transaction::getStatus, req.getStatus());
        }
        if (req.getStartTime() != null) {
            wrapper.ge(Transaction::getTransactionTime, req.getStartTime());
        }
        if (req.getEndTime() != null) {
            wrapper.le(Transaction::getTransactionTime, req.getEndTime());
        }
        if (req.getMinAmount() != null) {
            wrapper.ge(Transaction::getAmount, req.getMinAmount());
        }
        if (req.getMaxAmount() != null) {
            wrapper.le(Transaction::getAmount, req.getMaxAmount());
        }

        wrapper.orderByDesc(Transaction::getTransactionTime);

        // 分页查询
        IPage<Transaction> page = req.toPage();
        IPage<Transaction> resultPage = transactionMapper.selectPage(page, wrapper);

        // 转换为VO
        IPage<TransactionVO> voPage = resultPage.convert(entity -> {
            TransactionVO vo = new TransactionVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        });

        return PageResult.from(voPage);
    }

    @Override
    public TransactionDailySummary getDailySummary(Long customerId, LocalDate date, String transactionType) {
        log.debug("查询日汇总: customerId={}, date={}, type={}", customerId, date, transactionType);

        LambdaQueryWrapper<TransactionDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionDailySummary::getCustomerId, customerId)
                .eq(TransactionDailySummary::getSummaryDate, date)
                .eq(TransactionDailySummary::getTransactionType, transactionType);

        return dailySummaryMapper.selectOne(wrapper);
    }

    /**
     * 更新交易日汇总
     * 1. 使用Redis INCRBYFLOAT进行实时金额累加
     * 2. 更新或插入DB日汇总记录
     */
    private void updateDailySummary(Transaction transaction) {
        LocalDate today = LocalDate.now();
        String type = transaction.getTransactionType();
        Long customerId = transaction.getCustomerId();

        // 1. Redis实时计数
        String redisKey = SUMMARY_KEY_PREFIX + customerId + ":" + today.format(DATE_FMT) + ":" + type;
        try {
            redisTemplate.opsForValue().increment(redisKey, transaction.getAmount().doubleValue());
            // 设置过期时间为30天
            redisTemplate.expire(redisKey, 30, TimeUnit.DAYS);
            log.debug("Redis日汇总更新: key={}, amount={}", redisKey, transaction.getAmount());
        } catch (Exception e) {
            log.warn("Redis日汇总更新失败: {}", e.getMessage());
        }

        // 2. DB日汇总记录更新
        LambdaQueryWrapper<TransactionDailySummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionDailySummary::getCustomerId, customerId)
                .eq(TransactionDailySummary::getSummaryDate, today)
                .eq(TransactionDailySummary::getTransactionType, type);

        TransactionDailySummary summary = dailySummaryMapper.selectOne(wrapper);
        if (summary != null) {
            // 更新已有记录
            summary.setTotalAmount(summary.getTotalAmount().add(transaction.getAmount()));
            summary.setTransactionCount(summary.getTransactionCount() + 1);
            summary.setUpdatedTime(java.time.LocalDateTime.now());
            dailySummaryMapper.updateById(summary);
        } else {
            // 新增汇总记录
            summary = new TransactionDailySummary();
            summary.setCustomerId(customerId);
            summary.setSummaryDate(today);
            summary.setTransactionType(type);
            summary.setPaymentMethod(transaction.getPaymentMethod());
            summary.setIsCrossBorder(transaction.getIsCrossBorder());
            summary.setTotalAmount(transaction.getAmount());
            summary.setTransactionCount(1);
            summary.setLargeTxnFlag(false);
            summary.setCreatedTime(java.time.LocalDateTime.now());
            summary.setUpdatedTime(java.time.LocalDateTime.now());
            dailySummaryMapper.insert(summary);
        }
        log.debug("DB日汇总更新完成: customerId={}, date={}, type={}", customerId, today, type);
    }
}
